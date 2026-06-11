# C2 — Consumer lag & backpressure: when consumers can't keep up

> 🏫 **Classroom track** · ~35 min · Producers and consumers run at their own speeds. When a
> consumer falls behind, the gap has a name — **lag** — and it's the single most important number
> to watch on a running Kafka system. This lab makes lag visible and shows how to reason about it.

## The idea

Every consumer group has, for each partition, two offsets:

- **log-end-offset** — how many records the producers have written (the end of the log).
- **current-offset** — how far the group has committed (how much it has processed).

Their difference is **lag**:

```
lag = log-end-offset  −  current-offset   =   records produced but not yet processed
```

Lag near zero means consumers are keeping up. **Growing** lag means producers are outrunning
consumers — work is piling up. If it grows without bound you have a real incident: data is getting
older and older before anyone acts on it, and in the worst case it ages out of the topic
(retention) before you ever process it.

`kafka-consumer-groups --describe` shows all three numbers, so you can read lag directly — no code.

## Objectives

1. Read lag from `kafka-consumer-groups --describe`.
2. Watch lag appear as a standing backlog and then **drain** as a consumer catches up.
3. Watch lag **grow** when producers run and no consumer keeps up.

## Prerequisites

The shared single-broker stack running (`cd docker && docker compose up -d`).

## Setup — a backlog

Create a topic and write 1000 records into it (using the perf-test tool just as a quick way to
produce a known number of records):

```bash
docker exec broker kafka-topics --bootstrap-server :9092 \
  --create --topic lag-demo --partitions 1 --replication-factor 1

docker exec broker kafka-producer-perf-test --topic lag-demo \
  --num-records 1000 --record-size 100 --throughput -1 \
  --producer-props bootstrap.servers=:9092 acks=1
```

Confirm 1000 records are waiting:

```bash
docker exec broker kafka-get-offsets --bootstrap-server :9092 --topic lag-demo
# lag-demo:0:1000
```

## Step 1 — A consumer that falls behind

Run a consumer in the group `reporting` that processes only **200** of the 1000 records and then
stops — standing in for a consumer that started late, or is just too slow:

```bash
docker exec broker kafka-console-consumer --bootstrap-server :9092 --topic lag-demo \
  --group reporting --from-beginning --max-messages 200
```

Now ask Kafka how far behind the group is:

```bash
docker exec broker kafka-consumer-groups --bootstrap-server :9092 --describe --group reporting
```

```
GROUP      TOPIC     PARTITION  CURRENT-OFFSET  LOG-END-OFFSET  LAG
reporting  lag-demo  0          200             1000            800
```

There it is: **800 records of lag**. The group has processed 200 of the 1000 that exist. On a
dashboard this is the number that would be climbing during an incident.

## Step 2 — Watch it drain

Start the consumer again with no limit and let it catch up (it stops after 5s of silence):

```bash
docker exec broker kafka-console-consumer --bootstrap-server :9092 --topic lag-demo \
  --group reporting --timeout-ms 5000 > /dev/null
```

Re-check the lag:

```bash
docker exec broker kafka-consumer-groups --bootstrap-server :9092 --describe --group reporting
```

```
GROUP      TOPIC     PARTITION  CURRENT-OFFSET  LOG-END-OFFSET  LAG
reporting  lag-demo  0          1000            1000            0
```

Lag is **0** — the consumer caught up. current-offset reached log-end-offset.

## Step 3 — Watch it grow

Now the failure direction. The group is idle (no consumer running). Produce 500 more records and
watch lag appear with nothing to process them:

```bash
docker exec broker kafka-producer-perf-test --topic lag-demo \
  --num-records 500 --record-size 100 --throughput -1 \
  --producer-props bootstrap.servers=:9092 acks=1

docker exec broker kafka-consumer-groups --bootstrap-server :9092 --describe --group reporting
```

```
GROUP      TOPIC     PARTITION  CURRENT-OFFSET  LOG-END-OFFSET  LAG
reporting  lag-demo  0          1000            1500            500
```

Lag jumped to 500. log-end-offset moved (producers wrote more) while current-offset stood still
(no consumer). **A consumer that is down or too slow looks exactly like this — and the number
only goes up until something consumes.** Open the Kafka UI (<http://localhost:8080>) and you can
watch the same lag number on the `reporting` group's page.

## Discussion

- **Lag is your early-warning signal.** Steady ≈ 0 is healthy. A steadily rising line means
  producers are outrunning consumers, and you have minutes-to-hours before it hurts. Almost every
  Kafka production alert is "consumer group lag > X" or "lag rising for Y minutes."
- **How do you make lag drain faster?** The usual levers:
  - **Add consumers** to the group — but only up to the **partition count** (a partition is owned
    by at most one consumer; extra consumers sit idle). If `lag-demo` had more partitions you
    could parallelise; with one partition, one consumer is the ceiling. This is why partition
    count is a throughput decision (see the
    [patient-monitoring design](../../04-Implement-Topics-And-Partitions/patient-monitoring-exercise-full.md)).
  - **Make each consumer faster** — do less per record, batch writes to your database, offload
    slow work.
  - **Add partitions** (and consumers) if a single partition can't keep up.
- **Backpressure in Kafka is "polite".** Unlike a synchronous system, a slow consumer doesn't slow
  the producer — Kafka just buffers the backlog in the log. That's a feature (producers stay fast,
  spikes get absorbed) and a trap (lag can grow silently until retention deletes unprocessed data).
  **Watch lag, set retention with your worst-case recovery time in mind.**
- **Lag is per group.** Two different consumer groups reading the same topic have independent lag —
  your billing pipeline can be caught up while your analytics pipeline is hours behind.

## Cleanup

```bash
docker exec broker kafka-consumer-groups --bootstrap-server :9092 --delete --group reporting 2>/dev/null
docker exec broker kafka-topics --bootstrap-server :9092 --delete --topic lag-demo
```
