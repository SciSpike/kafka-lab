# D1 — Schema Registry: schema evolution and the breaking change

> 🏫 **Classroom track · BONUS / OPTIONAL** · ~45 min · Not part of the timed 7-chapter flow — run it
> when a group cares about schemas (most do, eventually). It needs one extra service (a Schema
> Registry) which you start on top of your normal stack just for this lab. Console-tools only; no Java.

## The idea

So far every message has been a plain string. Real systems send **structured** records (Avro,
Protobuf, JSON Schema) and hit a problem the
[design lab](../../04-Implement-Topics-And-Partitions/patient-monitoring-exercise-full.md) waved away
with *"let's assume you can use JSON"*: **how do producers and consumers agree on the shape of the
data, and what happens when that shape changes?**

A **Schema Registry** is the answer. It's a separate service that stores schemas, each under a
**subject** (by default `<topic>-value`) with a **version** history. The serializers do the rest:

- a producer registers its schema and writes only a tiny **schema id** into each message (not the
  schema itself);
- a consumer reads the id and fetches the matching schema from the registry to deserialize.

The payoff — and the failure this lab is about — is **compatibility enforcement**. Each subject has a
compatibility rule (default **`BACKWARD`**). When a producer tries to register a *new* version, the
registry checks it against the old one and **rejects it if it would break existing consumers**. That
turns "someone changed the schema and the night shift's consumers all crashed" into a clean,
immediate error at deploy time.

## Objectives

1. Produce and consume **Avro** through a Schema Registry, with the schema stored centrally.
2. Evolve the schema **compatibly** (add a field with a default) and watch a new version register.
3. Evolve it **incompatibly** and watch the registry **reject** the change — the breaking change
   caught before it ships.

## Prerequisites

Your usual Kafka stack running (single-broker or the classroom cluster). This lab adds a Schema
Registry on top; the first start pulls `confluentinc/cp-schema-registry:7.9.0`.

## Step 1 — Start the Schema Registry (on top of your stack)

From this lab's directory:

```bash
docker compose -f docker-compose-schema-registry.yaml up -d
```

It joins your existing `docker_kafka_network` and serves its REST API on <http://localhost:8081>.
Wait until it answers, and note the default compatibility rule:

```bash
curl -s http://localhost:8081/subjects        # []  (no schemas yet)
curl -s http://localhost:8081/config          # {"compatibilityLevel":"BACKWARD"}
```

## Step 2 — Produce Avro (schema registered automatically)

Use `kafka-avro-console-producer` (it ships in the registry image). The producer takes the schema
inline and reads JSON records from stdin; each record is encoded as Avro and the schema is registered
for you. (Run the CLI from inside the `schema-registry` container so the tools and the registry are
right there.)

```bash
printf '%s\n' '{"id":1,"name":"Alice"}' '{"id":2,"name":"Bob"}' \
  | docker exec -i schema-registry kafka-avro-console-producer \
      --bootstrap-server broker:29092 --topic users \
      --property schema.registry.url=http://localhost:8081 \
      --property value.schema='{"type":"record","name":"User","fields":[
          {"name":"id","type":"int"},
          {"name":"name","type":"string"}]}'
```

The registry now has a subject with one version:

```bash
curl -s http://localhost:8081/subjects                       # ["users-value"]
curl -s http://localhost:8081/subjects/users-value/versions  # [1]
curl -s http://localhost:8081/subjects/users-value/versions/1 | head -c 300
```

## Step 3 — Consume it back

```bash
docker exec schema-registry kafka-avro-console-consumer \
  --bootstrap-server broker:29092 --topic users --from-beginning \
  --property schema.registry.url=http://localhost:8081 --timeout-ms 8000 2>/dev/null | grep '^{'
```

```
{"id":1,"name":"Alice"}
{"id":2,"name":"Bob"}
```

The messages on the wire carried only a schema id; the consumer fetched the schema from the registry
to turn them back into records. (`grep '^{'` just hides the consumer's noisy startup logging.)

## Step 4 — Evolve it **compatibly** (add a field *with a default*)

Add an `email` field, giving it a **default** so the new schema can still read old records that don't
have it. Under `BACKWARD` compatibility that's allowed:

```bash
printf '%s\n' '{"id":3,"name":"Carol","email":"carol@example.com"}' \
  | docker exec -i schema-registry kafka-avro-console-producer \
      --bootstrap-server broker:29092 --topic users \
      --property schema.registry.url=http://localhost:8081 \
      --property value.schema='{"type":"record","name":"User","fields":[
          {"name":"id","type":"int"},
          {"name":"name","type":"string"},
          {"name":"email","type":"string","default":""}]}'

curl -s http://localhost:8081/subjects/users-value/versions   # [1,2]  -- version 2 registered
```

It worked, and there are now two versions. Old consumers (which know only v1) keep working; new ones
get the `email` field.

## Step 5 — Evolve it **incompatibly** (the breaking change)

Now make the classic mistake: add a **required** field with **no default** (`age`). The new schema
could not read the old records (they have no `age`), so it breaks `BACKWARD` compatibility:

```bash
printf '%s\n' '{"id":4,"name":"Dave","age":40}' \
  | docker exec -i schema-registry kafka-avro-console-producer \
      --bootstrap-server broker:29092 --topic users \
      --property schema.registry.url=http://localhost:8081 \
      --property value.schema='{"type":"record","name":"User","fields":[
          {"name":"id","type":"int"},
          {"name":"name","type":"string"},
          {"name":"age","type":"int"}]}'
```

The produce **fails** — the registry refuses to register the schema:

```
... RestClientException ...
Schema being registered is incompatible with an earlier schema for subject "users-value";
error code: 409
```

And nothing was added — still two versions:

```bash
curl -s http://localhost:8081/subjects/users-value/versions   # [1,2]  -- v3 was rejected
```

**That is the whole point.** The breaking change was caught at the source, immediately, instead of
silently shipping and taking down every consumer downstream. You can also ask the registry
*"would this be compatible?"* without trying to register it:

```bash
curl -s -X POST -H "Content-Type: application/json" \
  --data '{"schema": "{\"type\":\"record\",\"name\":\"User\",\"fields\":[{\"name\":\"id\",\"type\":\"int\"},{\"name\":\"name\",\"type\":\"string\"},{\"name\":\"age\",\"type\":\"int\"}]}"}' \
  http://localhost:8081/compatibility/subjects/users-value/versions/latest
# {"is_compatible":false}
```

## Discussion

- **Compatibility modes decide what "a safe change" means.** `BACKWARD` (the default) = new consumers
  can read old data → you may **delete** fields and **add fields with defaults**. `FORWARD` = old
  consumers can read new data → the opposite (add freely, delete only fields with defaults).
  `FULL` = both. `NONE` = anything goes (you opt out of safety). Set it per subject with
  `PUT /config/users-value`.
- **How to ship the change you actually wanted.** To add a required `age`: give it a default; or make
  the type nullable (`["null","int"]`); or, if it's genuinely a new kind of message, use a **new
  topic/subject**. The registry is forcing you to think about your existing data — which is exactly
  what you want.
- **The schema isn't in the message.** Only a 4-byte id is, so Avro records stay small; the schema
  lives once in the registry. This is why a consumer *must* be able to reach the registry.
- **Licensing footnote.** This used Confluent's `cp-schema-registry` (free to self-host, source-
  available under the Confluent Community License). For a strictly OSI-licensed registry with the
  *same* REST API and the *same* lab commands, swap the image in the compose file for **Karapace**
  (Aiven) or **Apicurio** (Red Hat) — the concepts above are standardised across all of them.

## Cleanup

```bash
docker exec broker kafka-topics --bootstrap-server :9092 --delete --topic users
docker compose -f docker-compose-schema-registry.yaml down
```
