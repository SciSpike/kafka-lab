# A1 — Crash before the ack: `acks` and lost writes

> 🏫 **Classroom track** · ~40 min · Extends
> [`find-the-flaw.md`](../../04-Implement-Topics-And-Partitions/find-the-flaw.md) question 1:
> *"What happens if the producing client goes down and there are messages in the buffer that
> have not been sent to Kafka?"* Here we make that happen and watch it.

## The idea

When your application hands a record to a Kafka producer, the record is **not** instantly
safe on the cluster. It travels through a client-side buffer, over the network, to the
broker, which appends it to the log and (optionally) replicates it — and only then sends
back an **acknowledgement (ack)**.

The `acks` setting decides how much confirmation the producer waits for:

| `acks` | The producer considers the send "done" when… | Risk |
| ------ | --------------------------------------------- | ---- |
| `0`    | it has written the record to its socket — **no confirmation at all** | records can vanish and the producer never knows |
| `1`    | the **leader** broker has written it to its log | a record acked by a leader that then fails before replication can be lost |
| `all`  | the leader **and all in-sync replicas** have it | strongest; needs more than one broker to mean anything (see C1) |

In this exercise you will see the difference between a record that is **confirmed by the
cluster** and one that is merely **"sent" but unconfirmed** — and what a crash does to each.

We use the `kafka-verifiable-producer` tool, which prints one JSON line per record describing
exactly what happened, so there is no Java to read.

## Objectives

1. See a *confirmed* send (`acks=1`) carry a real log offset.
2. See an *unconfirmed* send (`acks=0`) claim success with **no** offset.
3. Crash a producer mid-stream and observe that it never gets to reconcile what it sent.

## Prerequisites

The **classroom cluster** running (start it once at the beginning of the course and leave it up — see [classroom-labs.md](../classroom-labs.md)). Two terminals.

## Setup — create the topic

```bash
docker exec broker kafka-topics --bootstrap-server :9092 \
  --create --topic crashtest --partitions 1 --replication-factor 1
```

## Step 1 — A confirmed send (`acks=1`)

Run a short producer that sends 20 records and exits cleanly:

```bash
docker run --rm --network docker_kafka_network confluentinc/confluent-local:7.4.1 \
  kafka-verifiable-producer --bootstrap-server broker:29092 --topic crashtest \
  --max-messages 20 --throughput 50 --acks 1
```

Look at the output. Each record produces a line like:

```json
{"timestamp":...,"name":"producer_send_success","key":null,"value":"7","offset":7,"topic":"crashtest","partition":0}
```

…and at the very end, a reconciliation summary:

```json
{"timestamp":...,"name":"shutdown_complete"}
{"timestamp":...,"name":"tool_data","sent":20,"acked":20,"target_throughput":50,"avg_throughput":...}
```

Two things to notice:

- Every record carries a real **`"offset"`** (0, 1, 2, …). That offset *is* the cluster's
  receipt — proof the record is durably in the log.
- The final `tool_data` line says **`"sent":20,"acked":20`**. The producer sent 20 and the
  cluster confirmed all 20.

Confirm the cluster agrees — it should report 20 records:

```bash
docker exec broker kafka-get-offsets --bootstrap-server :9092 --topic crashtest
# crashtest:0:20
```

## Step 2 — An *unconfirmed* send (`acks=0`)

Now the same thing, but fire-and-forget. (Re-create the topic first so the count is clean.)

```bash
docker exec broker kafka-topics --bootstrap-server :9092 --delete --topic crashtest
sleep 3
docker exec broker kafka-topics --bootstrap-server :9092 \
  --create --topic crashtest --partitions 1 --replication-factor 1

docker run --rm --network docker_kafka_network confluentinc/confluent-local:7.4.1 \
  kafka-verifiable-producer --bootstrap-server broker:29092 --topic crashtest \
  --max-messages 20 --throughput 50 --acks 0
```

Look closely at the success lines now:

```json
{"timestamp":...,"name":"producer_send_success","key":null,"value":"7","offset":-1,"topic":"crashtest","partition":0}
```

The offset is **`-1`**. That is the whole lesson of this step: with `acks=0`, the producer
reports the record as a *success* even though **it has no idea whether the cluster received
it**. `offset:-1` literally means *"sent, but not confirmed."* This is exactly the dangerous
middle state — the application has moved on, believing the data is safe, but nothing
guarantees it.

> 💡 The records may well have arrived this time (a healthy local broker keeps up). The point
> is not that they were lost — it is that **the producer cannot tell you either way**. With
> `acks=0` there is no receipt.

## Step 3 — Crash the producer mid-stream

Now let's actually kill a producer while it is working. We give it far more work than it can
finish instantly so there is something in flight when we pull the plug.

**Terminal 1** — start a long-running, named producer (fire-and-forget):

```bash
docker run --rm --name doomed --network docker_kafka_network confluentinc/confluent-local:7.4.1 \
  kafka-verifiable-producer --bootstrap-server broker:29092 --topic crashtest \
  --max-messages 1000000 --throughput 5000 --acks 0
```

You will see `producer_send_success` lines streaming by.

**Terminal 2** — after a second or two, **hard-crash** it (SIGKILL — like `kill -9` or a power loss):

```bash
docker kill doomed
```

Back in Terminal 1, notice what is **missing**: there is **no `shutdown_complete` and no
`tool_data` summary**. The producer died before it could reconcile what it had sent versus
what was confirmed. A real application crashing here would have no record of which in-flight
messages made it.

Now compare what the cluster actually stored against the last `value` the producer printed:

```bash
docker exec broker kafka-get-offsets --bootstrap-server :9092 --topic crashtest
```

> 🔬 **Contrast it with a graceful shutdown.** Run the same Terminal-1 command again, but this
> time in Terminal 2 use `docker stop doomed` (SIGTERM) instead of `docker kill`. A graceful
> stop lets the client **flush its buffer** and print the `tool_data` summary — the in-flight
> records are not abandoned. `docker stop` ≈ a clean deployment/redeploy; `docker kill` ≈ a
> crash, OOM-kill, or yanked power cable.

## Discussion

Tie this back to [`find-the-flaw.md`](../../04-Implement-Topics-And-Partitions/find-the-flaw.md):

1. **"Messages in the buffer that have not been sent"** — that buffer is real (client-side
   batching). A hard crash abandons it. A graceful shutdown (close/flush) drains it.
2. **`acks` is a durability-vs-speed dial, not a yes/no.** `acks=0` is fastest and least
   safe; `acks=all` is safest and needs replication to be meaningful (see exercise C1, where
   we add more brokers).
3. **The fix is rarely "just set `acks=all`".** Real producers also rely on:
   - `enable.idempotence=true` — so a retried send isn't written twice;
   - `retries` / `delivery.timeout.ms` — so transient failures are retried instead of dropped;
   - **graceful shutdown** — actually calling `flush()`/`close()` on the way down (handle
     SIGTERM!), which is what `docker stop` modelled above.

### Optional experiment

Make a small results table. For each `acks` value, note the average throughput from the
`tool_data` line in Step 1 (bump `--max-messages` to e.g. `100000` for a more stable number):

| `acks` | avg throughput (records/sec) | offset on success lines | safe on crash? |
| ------ | ---------------------------- | ----------------------- | -------------- |
| `0`    |                              | `-1` (no receipt)       | no             |
| `1`    |                              | real offset             | leader only    |
| `all`  |                              | real offset             | strongest\*    |

\* Because `crashtest` is a **single-replica (RF=1)** topic, `all` behaves like `1` here — there are
no followers to wait for. C1 revisits this with a replicated (RF=3) topic, where `acks=all` finally bites.

## Cleanup

```bash
docker exec broker kafka-topics --bootstrap-server :9092 --delete --topic crashtest
```
