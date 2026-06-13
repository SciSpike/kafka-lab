# C1 — Multi-broker cluster: replication, ISR, `acks=all`, and broker failure

> 🏫 **Classroom track** · ~45 min · The Kafka stack you've been using all course is **already a
> 3-broker cluster** — here we finally use the second and third brokers: replicate a topic across
> them, kill one, and watch your data survive. This is also where the `acks=all` durability setting
> first earns its keep — you'll push the `acks` dial further in the
> [exactly-once labs](../A1-Crash-Before-Ack/crash-before-ack.md) later in the course.

## The idea

Durability in Kafka comes from **replication**: each partition has a `replication-factor`
copies, spread across brokers. One copy is the **leader** (handles all reads/writes); the others
are **followers** that copy from it. The set of replicas that are fully caught up is the
**in-sync replica set (ISR)**.

Two settings decide how safe a write is:

- **`acks`** (producer side): `all` means "wait until every in-sync replica has the
  record", not just the leader.
- **`min.insync.replicas`** (topic side): the minimum ISR size for an `acks=all` write to be
  *accepted at all*. If the ISR drops below this, the leader **rejects** `acks=all` writes rather
  than risk acknowledging data that isn't replicated enough.

Together they answer: *"how many broker failures can I survive without losing an acknowledged
write?"*

In this exercise you will create replicated topics, kill a broker, and watch (a) the cluster
elect a new leader and keep your data, and (b) `min.insync.replicas` refuse unsafe writes.

## Objectives

1. See a partition replicated across all three brokers of the classroom cluster.
2. Kill a broker and watch the ISR shrink and a new leader get elected — with no data loss.
3. See `min.insync.replicas` reject `acks=all` when there aren't enough replicas.

## Prerequisites

The **classroom cluster** running — it's the standing environment for this course (the three
brokers `broker`, `broker-2`, `broker-3`), so it should already be up from the start of the course.
If not: `cd docker && docker compose -f docker-compose-classroom.yaml up -d`.

## ⚠️ Ground rules for this lab (read first)

You're about to stop brokers in the cluster the *whole class* is using, so a couple of rules:

- **Stop only ONE broker at a time, and bring it back before stopping another.** Each node is both
  a broker *and* a controller (it runs the cluster's "brain"). The brain needs a majority (2 of 3)
  to function. Stopping two at once freezes the cluster's metadata and the demos misbehave. One down
  is always safe — and **leave all three running when you finish**, for the rest of the course.
- **After stopping a broker, wait ~10–15 seconds** before checking the ISR — the cluster takes a
  few seconds (a broker "session timeout") to notice a broker is gone.
- **Always point commands at a broker that is still up.** When a broker is down, clients print
  some scary-looking `UnknownHostException` / "Error connecting to node" warnings as they notice
  the missing broker. **That noise is expected** — look past it for the meaningful line.

## Step 1 — Confirm the three brokers

All three nodes should be in the controller quorum:

```bash
docker exec broker kafka-metadata-quorum --bootstrap-server :9092 describe --status \
  | grep -E 'LeaderId|CurrentVoters'
# CurrentVoters:  [1,2,3]
```

You can also open the Kafka UI at <http://localhost:8080> and see three brokers.

## Step 2 — A replicated topic

Create a topic replicated across all three brokers, requiring at least two in-sync replicas for
an `acks=all` write:

```bash
docker exec broker kafka-topics --bootstrap-server :9092 \
  --create --topic pay-safe --partitions 1 --replication-factor 3 \
  --config min.insync.replicas=2
```

Look at how it's laid out:

```bash
docker exec broker kafka-topics --bootstrap-server :9092 --describe --topic pay-safe
```

```
Topic: pay-safe  Partition: 0  Leader: 3  Replicas: 2,3,1  Isr: 2,3,1
```

`Replicas: 2,3,1` — a copy lives on all three brokers. `Isr: 2,3,1` — all three are currently
caught up. `Leader: 3` — node 3 currently serves this partition (yours may differ).

Produce a few records with the strongest guarantee, `acks=all`:

```bash
printf 'safe-%s\n' 1 2 | docker exec -i broker kafka-console-producer \
  --bootstrap-server :9092 --topic pay-safe --producer-property acks=all
```

## Step 3 — Kill a broker: failover with no data loss

Find the current leader for `pay-safe` (the `Leader:` number from the describe above) and stop
**that** broker to force a leader election. The node ids map to container names `broker` (1),
`broker-2` (2), `broker-3` (3). For example, if the leader is node 3:

```bash
docker stop broker-3
```

Wait ~12 seconds, then describe the topic **from a broker that is still up** (use `broker` unless
that's the one you stopped, in which case use `broker-2`):

```bash
docker exec broker kafka-topics --bootstrap-server :9092 --describe --topic pay-safe
```

```
Topic: pay-safe  Partition: 0  Leader: 1  Replicas: 2,3,1  Isr: 1,2
```

Two things happened automatically:

- **A new leader was elected** (here node 1) from the surviving in-sync replicas.
- **The ISR shrank** to the two brokers that are still up.

Now prove the data survived the death of the broker that was leader when it was written (point the
throwaway consumer at any broker that's still up):

```bash
docker run --rm --network docker_kafka_network confluentinc/confluent-local:7.4.1 \
  kafka-console-consumer --bootstrap-server broker:29092 --topic pay-safe \
  --from-beginning --timeout-ms 6000
# safe-1
# safe-2
```

`acks=all` writes were replicated before they were acknowledged, so losing one broker lost
nothing. **This is the durability that a single broker simply cannot give you.**

Bring the broker back before continuing (and wait ~15s for it to rejoin the ISR):

```bash
docker start broker-3
sleep 15
docker exec broker kafka-topics --bootstrap-server :9092 --describe --topic pay-safe
# Isr should be back to all three
```

## Step 4 — `min.insync.replicas`: refusing an unsafe write

How safe is "safe enough"? That is exactly what `min.insync.replicas` controls. Create a
**strict** topic that demands all three replicas be in sync:

```bash
docker exec broker kafka-topics --bootstrap-server :9092 \
  --create --topic pay-strict --partitions 1 --replication-factor 3 \
  --config min.insync.replicas=3
```

Now stop **one** broker (any one — but only one!) and wait ~12s. We'll stop `broker-3` and run the
rest from `broker`:

```bash
docker stop broker-3
sleep 12
```

Check the ISR — it has dropped to 2, which is **below** the strict topic's required 3:

```bash
docker exec broker kafka-topics --bootstrap-server :9092 --describe --topic pay-strict
# ... Isr: 1,2   (only two — but min.insync.replicas=3)
```

Try to produce with `acks=all`. It will **fail** — the leader refuses to acknowledge a write it
can't replicate enough:

```bash
printf 'strict-1\n' | docker exec -i broker kafka-console-producer \
  --bootstrap-server :9092 --topic pay-strict \
  --producer-property acks=all \
  --producer-property delivery.timeout.ms=6000 --producer-property request.timeout.ms=2500
```

Amid the connection-warning noise you will see the meaningful error:

```
org.apache.kafka.common.errors.NotEnoughReplicasException
... NOT_ENOUGH_REPLICAS ...
```

This is Kafka **protecting you**: it would rather reject the write than accept data that isn't
safely replicated. Now show that the *same broker, same moment* will happily take an `acks=1`
write — because `acks=1` only needs the leader:

```bash
printf 'strict-2\n' | docker exec -i broker kafka-console-producer \
  --bootstrap-server :9092 --topic pay-strict --producer-property acks=1
# succeeds
```

That contrast is the whole lesson: **`acks=1` traded safety for availability** (it kept working,
but that record is only on one broker), while **`acks=all` + `min.insync.replicas=3` chose safety
over availability** (it refused, so you never get a false promise of durability).

Bring the broker back — and confirm all three are healthy again before moving on, since the rest of
the course uses this cluster:

```bash
docker start broker-3
sleep 15
docker exec broker kafka-topics --bootstrap-server :9092 --describe --topic pay-strict
# Isr back to 1,2,3
```

## Discussion

- **The durability knob has three parts that must agree:** `replication-factor` (how many copies
  exist), `acks=all` (wait for the in-sync copies), and `min.insync.replicas` (how many copies
  must exist for a write to be accepted). A common production setting is RF=3 + `acks=all` +
  `min.insync.replicas=2` — survives one broker failure with zero data loss, and stays available.
- **`min.insync.replicas=3` with RF=3 is usually too strict**: any single broker failure halts
  `acks=all` writes (you saw it). That's why "RF minus one" is the typical choice.
- **Availability vs. durability is a genuine tradeoff**, not a bug. `acks=1` stayed up but risked
  loss; strict `acks=all` refused the risk but stopped accepting writes. Your business decides
  which failure is worse.
- **Sets up the exactly-once labs ([A1](../A1-Crash-Before-Ack/crash-before-ack.md)):** the
  `acks=0`/`acks=1`/`acks=all` dial — and what a producer crash does to in-flight records — only
  reaches its full meaning because of the replication you just set up here.

### Optional explorations

- When you reach [B1 (producer tuning)](../B1-Producer-Tuning/producer-tuning.md), try it against a
  RF=3 topic and compare `acks=1` vs `acks=all` throughput — the gap is real here, because `all`
  waits for the network round-trip to followers.
- Create a topic with multiple partitions and watch how the UI spreads leaders across all three
  brokers for load balancing.

## Cleanup

Just delete the two topics — **leave the cluster running** for the rest of the course:

```bash
docker exec broker kafka-topics --bootstrap-server :9092 --delete --topic pay-safe
docker exec broker kafka-topics --bootstrap-server :9092 --delete --topic pay-strict
```
