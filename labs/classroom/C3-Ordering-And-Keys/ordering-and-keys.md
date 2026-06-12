# C3 — Ordering & keys: order is only guaranteed *within* a partition

> 🏫 **Classroom track** · ~35 min · This is the hands-on version of a decision you already met
> in the [patient-monitoring design](../../04-Implement-Topics-And-Partitions/patient-monitoring-exercise-full.md):
> *"since we want to make sure we process a single device stream in order, let's use the device id
> as the source for the partition key."* Here you'll see exactly what that buys you — and what
> happens without it.

## The idea

People often assume Kafka keeps messages "in order". It does — but only **within a single
partition**. Across partitions there is **no ordering guarantee** at all.

Which partition a record lands in is decided by its **key**:

- **With a key**, Kafka hashes the key and always sends that key to the **same partition**. So
  every record for `device-42` goes to one partition, and that device's events are read back in
  the exact order they were produced.
- **Without a key**, Kafka spreads records across partitions for load balancing. Two records you
  *think* are related can land on different partitions — and then nothing guarantees the order
  you read them in.

So "use the right key" is how you choose your **ordering unit**: per device, per patient, per
account — whatever must stay in order.

## Objectives

1. See that a keyed record always lands in the same partition, preserving order for that key.
2. See that without a key, a single logical stream scatters across partitions and loses order.
3. Connect this to choosing a partition key in a real design.

## Prerequisites

The **classroom cluster** running (start it once at the beginning of the course and leave it up — see [classroom-labs.md](../classroom-labs.md)).

## Step 1 — Keyed: each device stays in one partition, in order

Create a topic with three partitions and produce **interleaved** readings from two devices, using
the **device id as the key** (`key:value`, split on `:`):

```bash
docker exec broker kafka-topics --bootstrap-server :9092 \
  --create --topic sensor-events --partitions 3 --replication-factor 1

printf '%s\n' \
  'scale-A:A-reading-1' 'bp-B:B-reading-1' 'scale-A:A-reading-2' 'bp-B:B-reading-2' \
  'scale-A:A-reading-3' 'bp-B:B-reading-3' 'scale-A:A-reading-4' 'bp-B:B-reading-4' \
  | docker exec -i broker kafka-console-producer --bootstrap-server :9092 --topic sensor-events \
    --property parse.key=true --property key.separator=:
```

Now read them back, showing the partition and key of each record:

```bash
docker exec broker kafka-console-consumer --bootstrap-server :9092 --topic sensor-events \
  --from-beginning --property print.partition=true --property print.key=true --timeout-ms 5000
```

Sort the output by partition to see the grouping clearly. Every `scale-A` record is in one
partition and every `bp-B` record is in another:

```
Partition:1   bp-B     B-reading-1
Partition:1   bp-B     B-reading-2
Partition:1   bp-B     B-reading-3
Partition:1   bp-B     B-reading-4
Partition:2   scale-A  A-reading-1
Partition:2   scale-A  A-reading-2
Partition:2   scale-A  A-reading-3
Partition:2   scale-A  A-reading-4
```

Even though the two devices' readings were **interleaved** when produced, each device's stream is
wholly inside a single partition, **in the order it was sent** (1, 2, 3, 4). Because a partition
is always read start-to-finish in order, `scale-A`'s readings can never be processed out of order.
That is the guarantee the design relied on.

## Step 2 — No key: a single device's stream scatters

Now the opposite. Create another topic and produce six readings from **one** device — but with
**no key**, and using the round-robin partitioner so the scatter is obvious and repeatable:

```bash
docker exec broker kafka-topics --bootstrap-server :9092 \
  --create --topic sensor-rr --partitions 3 --replication-factor 1

printf '%s\n' A-reading-1 A-reading-2 A-reading-3 A-reading-4 A-reading-5 A-reading-6 \
  | docker exec -i broker kafka-console-producer --bootstrap-server :9092 --topic sensor-rr \
    --producer-property partitioner.class=org.apache.kafka.clients.producer.RoundRobinPartitioner
```

Read them back with their partitions:

```bash
docker exec broker kafka-console-consumer --bootstrap-server :9092 --topic sensor-rr \
  --from-beginning --property print.partition=true --timeout-ms 5000 | sort
```

```
Partition:0   A-reading-2
Partition:0   A-reading-4
Partition:0   A-reading-6
Partition:2   A-reading-1
Partition:2   A-reading-3
Partition:2   A-reading-5
```

The **same device's** readings are now split across partitions. Reading 1 is in partition 2,
reading 2 is in partition 0. If two consumers in a group each take one of those partitions (as in
[A3](../A3-Rebalance-Reprocessing/rebalance-reprocessing.md)), they will process `A-reading-2` and
`A-reading-1` **independently and in parallel** — there is nothing to stop reading 2 being handled
before reading 1. The device's order is gone.

> 💡 The default partitioner (no key) is "sticky" — it sends a *batch* of records to one partition
> at a time, then switches, to be efficient. So a quick burst might happen to land together (try
> producing without the round-robin setting and watch `kafka-get-offsets`). The point is that
> *you don't control it* and there's no ordering tie to your data — only a **key** gives you that.

## Discussion

- **The key chooses your ordering unit.** Key by `device-id` → per-device order. Key by
  `patient-id` → all of a patient's events ordered (even across several devices), at the cost of
  funneling them through one partition. Key by `account-id`, `session-id`, … — pick whatever must
  not be reordered. This is one of the most important design decisions you make with Kafka.
- **Ordering and parallelism pull against each other.** All of one key's traffic goes to one
  partition, so a very "hot" key can't be spread across consumers for more throughput. The
  patient-monitoring design discusses exactly this trade: per-device ordering was achievable, but
  strict per-patient ordering across many high-rate devices would bottleneck on one partition.
- **No key = maximum spread, no order.** That's the right choice when records are independent
  (e.g. metrics you'll aggregate anyway) and you want even load across partitions and consumers.
- **More partitions never adds cross-partition ordering.** Adding partitions scales throughput but
  can only ever reduce ordering, never increase it. If you later change the key or partition count,
  records for a key can move to a different partition — be deliberate about it.

## Cleanup

```bash
docker exec broker kafka-topics --bootstrap-server :9092 --delete --topic sensor-events
docker exec broker kafka-topics --bootstrap-server :9092 --delete --topic sensor-rr
```
