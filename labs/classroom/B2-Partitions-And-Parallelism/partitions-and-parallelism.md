# B2 — Partitions & consumer parallelism: where adding consumers stops helping

> 🏫 **Classroom track** · ~35 min · A natural follow-on to
> [A3](../A3-Rebalance-Reprocessing/rebalance-reprocessing.md) and the partition-sizing reasoning in
> the [patient-monitoring design](../../04-Implement-Topics-And-Partitions/patient-monitoring-exercise-full.md).
> The question: if my consumers can't keep up (lag is rising — see [C2](../C2-Consumer-Lag/consumer-lag.md)),
> can I just keep adding consumers? Answer: only up to a hard ceiling.

## The idea

Within a consumer group, **each partition is consumed by at most one consumer at a time.** A
consumer can own several partitions, but a partition is never split across consumers. That single
rule sets the ceiling on how far you can scale out a group:

```
maximum useful consumers in a group  =  number of partitions on the topic
```

Add a consumer while there's a free partition and it picks up work. Add one when every partition is
already taken and it just sits there **idle** — there's nothing left to give it. So the partition
count you chose at design time is the hard limit on consumer parallelism, forever (until you add
partitions).

## Objectives

1. Watch partitions get shared out as consumers join a group.
2. See a consumer go **idle** the moment the group has more consumers than partitions.
3. See that a 1-partition topic can't be scaled out at all.

## Prerequisites

The **classroom cluster** running (start it once at the beginning of the course and leave it up — see [classroom-labs.md](../classroom-labs.md)). You'll want several
terminals — one per consumer, plus one for inspecting the group.

## Step 1 — A 3-partition topic and a backlog

```bash
docker exec broker kafka-topics --bootstrap-server :9092 \
  --create --topic events --partitions 3 --replication-factor 1
docker exec broker kafka-producer-perf-test --topic events \
  --num-records 3000 --record-size 100 --throughput -1 \
  --producer-props bootstrap.servers=:9092 acks=1
```

## Step 2 — Add consumers one at a time and watch the assignment

Start consumers in the group `workers`, **one per terminal**, adding them one at a time:

```bash
docker run --rm --network docker_kafka_network confluentinc/confluent-local:7.4.1 \
  kafka-console-consumer --bootstrap-server broker:29092 --topic events --group workers --from-beginning
```

After each consumer joins, check how the 3 partitions are shared — the `#PARTITIONS` column is the
number each member owns:

```bash
docker exec broker kafka-consumer-groups --bootstrap-server :9092 --describe --group workers --members
```

Watch it evolve as you go from 1 → 2 → 3 → 4 consumers:

| Consumers running | Partitions per consumer | Notes |
|-------------------|-------------------------|-------|
| 1 | `3` | one consumer does everything |
| 2 | `2`, `1` | work is shared |
| 3 | `1`, `1`, `1` | fully parallel — every partition has its own consumer |
| 4 | `1`, `1`, `1`, **`0`** | the 4th consumer is **idle** |

With four consumers on three partitions, the `--members` output literally shows one member owning
`0` partitions:

```
GROUP    CONSUMER-ID              ...  #PARTITIONS
workers  console-consumer-31763...      1
workers  console-consumer-8445d...      1
workers  console-consumer-186bb...      1
workers  console-consumer-a7189...      0      <- idle: nothing left to assign
```

That idle consumer consumes no records, contributes no throughput, and just waits for a partition to
free up (e.g. if one of the others dies). **Adding it did nothing** — you hit the ceiling at three.

## Step 3 — A 1-partition topic can't scale at all

To make the ceiling vivid, repeat with a single partition:

```bash
docker exec broker kafka-topics --bootstrap-server :9092 \
  --create --topic single --partitions 1 --replication-factor 1
docker exec broker kafka-producer-perf-test --topic single \
  --num-records 1000 --record-size 100 --throughput -1 \
  --producer-props bootstrap.servers=:9092 acks=1
```

Start two or three consumers in a new group on `single`, then describe it:

```bash
# (run each in its own terminal)
docker run --rm --network docker_kafka_network confluentinc/confluent-local:7.4.1 \
  kafka-console-consumer --bootstrap-server broker:29092 --topic single --group solo --from-beginning

docker exec broker kafka-consumer-groups --bootstrap-server :9092 --describe --group solo --members
```

No matter how many consumers you add, **exactly one** owns the single partition and does all the
work; the rest own `0`. A 1-partition topic is single-threaded on the consumer side — there is no
way to parallelise it within a group.

## Discussion

- **Partition count is a throughput decision you make once, up front.** It caps consumer parallelism
  forever. This is exactly the calculation in the patient-monitoring design (expected messages/sec ÷
  per-consumer throughput → partition count, then round up for headroom). Under-provision partitions
  and you can't scale out later without repartitioning.
- **More consumers than partitions isn't wrong — it's deliberate headroom.** Those idle consumers are
  hot standbys: if an active consumer crashes, a rebalance hands its partition to a waiting one
  ([A3](../A3-Rebalance-Reprocessing/rebalance-reprocessing.md)). You just shouldn't expect them to
  add throughput.
- **The other lever is the consumer itself.** If you're already at one-consumer-per-partition and
  still behind, your options are: add partitions (and consumers), or make each consumer faster
  (callback to [A4](../A4-Slow-Poll-Eviction/slow-poll-eviction.md) and
  [C2](../C2-Consumer-Lag/consumer-lag.md)).
- **Remember the cost of more partitions:** they buy parallelism but spread keys wider, which can
  work against ordering ([C3](../C3-Ordering-And-Keys/ordering-and-keys.md)). Throughput vs. ordering,
  again.

## Cleanup

```bash
docker exec broker kafka-consumer-groups --bootstrap-server :9092 --delete --group workers 2>/dev/null
docker exec broker kafka-consumer-groups --bootstrap-server :9092 --delete --group solo 2>/dev/null
docker exec broker kafka-topics --bootstrap-server :9092 --delete --topic events
docker exec broker kafka-topics --bootstrap-server :9092 --delete --topic single
```
