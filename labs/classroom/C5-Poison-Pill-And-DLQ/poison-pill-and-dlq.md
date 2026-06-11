# C5 — Poison pills, dead-letter topics & offset reset

> 🏫 **Classroom track** · ~40 min · A single un-processable record can wedge an entire consumer
> pipeline. This lab reproduces that "poison pill", then shows the two standard ways out — route it
> to a **dead-letter topic** and **skip past it** — using `kafka-consumer-groups --reset-offsets`,
> the operational tool you'll reach for whenever you need to move a consumer's position by hand.

## The idea

A consumer processes a partition strictly in order and only commits an offset once a record is
handled. Now suppose one record can never be handled — malformed JSON, a value that violates a
database constraint, a bug that throws on it. The consumer:

1. reads the bad record,
2. fails to process it,
3. so it never commits past it,
4. restarts/retries, reads the **same** bad record, fails again…

The pipeline is stuck on offset N forever, and **every good record behind it is blocked**. Lag
climbs and nothing moves. That one record is a **poison pill**.

There's no way to "fix" the record in place — the position is immutable. The operational responses
are:

- **Dead-letter it**: copy the bad record to a separate **dead-letter topic (DLQ)** for humans to
  inspect later, then move on. Nothing is lost; the pipeline is unblocked.
- **Skip it**: advance the consumer group's committed offset past the poison.

Both come down to moving the committed offset, which is exactly what `--reset-offsets` does.

## Objectives

1. Create a stuck consumer group parked on a poison record and see the lag it causes.
2. Copy the poison record to a dead-letter topic.
3. Skip the poison with `--reset-offsets --shift-by` and watch the pipeline drain.

## Prerequisites

The shared single-broker stack running (`cd docker && docker compose up -d`).

## Setup — a stream with a poison pill

```bash
docker exec broker kafka-topics --bootstrap-server :9092 \
  --create --topic payments --partitions 1 --replication-factor 1
docker exec broker kafka-topics --bootstrap-server :9092 \
  --create --topic payments-dlq --partitions 1 --replication-factor 1

printf '%s\n' pay-1 pay-2 POISON pay-3 pay-4 \
  | docker exec -i broker kafka-console-producer --bootstrap-server :9092 --topic payments
```

`POISON` sits at **offset 2**, with two good records before it and two after:

```bash
docker exec broker kafka-get-offsets --bootstrap-server :9092 --topic payments
# payments:0:5
```

## Step 1 — Get stuck on the poison

Our processor (group `settlement`) handles the two good records, commits, and then can't get past
the poison. We model "processed the first two, now wedged" by consuming exactly two records (which
commits offset 2 — the poison's position):

```bash
docker exec broker kafka-console-consumer --bootstrap-server :9092 --topic payments \
  --group settlement --from-beginning --max-messages 2 > /dev/null
```

Look at the group: it is parked at offset 2 with three records of lag, and it will not advance
because offset 2 is the poison:

```bash
docker exec broker kafka-consumer-groups --bootstrap-server :9092 --describe --group settlement
```

```
GROUP       TOPIC     PARTITION  CURRENT-OFFSET  LOG-END-OFFSET  LAG
settlement  payments  0          2               5               3
```

You can look the offending record straight in the eye by reading that one offset:

```bash
docker exec broker kafka-console-consumer --bootstrap-server :9092 --topic payments \
  --partition 0 --offset 2 --max-messages 1 --timeout-ms 5000
# POISON
```

In a real system the application would be throwing an exception on this record on every restart, and
`pay-3`/`pay-4` would be stranded behind it.

## Step 2 — Dead-letter the poison

Before skipping it, preserve it for investigation by copying offset 2 into the dead-letter topic —
read exactly that one record and pipe it into a producer for `payments-dlq`:

```bash
docker exec broker kafka-console-consumer --bootstrap-server :9092 --topic payments \
  --partition 0 --offset 2 --max-messages 1 --timeout-ms 5000 \
  | docker exec -i broker kafka-console-producer --bootstrap-server :9092 --topic payments-dlq
```

Confirm it's safely tucked away in the DLQ:

```bash
docker exec broker kafka-console-consumer --bootstrap-server :9092 --topic payments-dlq \
  --from-beginning --timeout-ms 4000
# POISON
```

Now a human (or a separate repair job) can examine `payments-dlq` later without holding up live
payments.

## Step 3 — Skip the poison and resume

Move the group's committed offset forward by one, stepping over offset 2.

> ⚠️ The consumer group must have **no active members** when you reset offsets — Kafka refuses to
> move offsets out from under a running consumer. Our consumer already exited, so we're fine; in
> production you stop the consumers first, reset, then start them again.

```bash
docker exec broker kafka-consumer-groups --bootstrap-server :9092 \
  --group settlement --topic payments --reset-offsets --shift-by 1 --execute
```

```
GROUP       TOPIC     PARTITION  NEW-OFFSET
settlement  payments  0          3
```

The group is now at offset 3 — past the poison. Resume processing and the stranded good records flow
through:

```bash
docker exec broker kafka-console-consumer --bootstrap-server :9092 --topic payments \
  --group settlement --timeout-ms 5000
# pay-3
# pay-4
```

Lag is back to zero, the pipeline is unblocked, and the poison is preserved in the DLQ rather than
lost:

```bash
docker exec broker kafka-consumer-groups --bootstrap-server :9092 --describe --group settlement
# ... LAG 0
```

## Discussion

- **Skipping is data loss unless you DLQ first.** A bare `--shift-by 1` throws the record away. The
  DLQ-then-skip sequence is what lets you unblock *now* and fix *later* — which is why production
  consumers usually have a try/catch that publishes failures to a `<topic>-dlq` automatically rather
  than crashing.
- **`--reset-offsets` is your offset Swiss-army knife.** Besides `--shift-by`, it takes
  `--to-offset N` (jump to an exact position), `--to-earliest` / `--to-latest` (reprocess everything
  / skip everything), `--to-datetime` (rewind to a point in time), and `--shift-by -N` (go *back* to
  reprocess). Add `--dry-run` instead of `--execute` to preview the change first — always do this on
  a real system.
- **Reprocessing is the same tool in reverse.** "We deployed a bug and need to re-run yesterday's
  data" is `--reset-offsets --to-datetime` + restart. Because consumption is just a movable offset,
  replay is a first-class operation — one of Kafka's superpowers over a traditional queue, where a
  consumed message is gone.
- **Poison handling connects back to idempotency** ([A2](../A2-Duplicate-Consumption/duplicate-consumption.md)):
  if your DLQ-and-skip logic itself retries, the good records around the poison can be processed
  more than once — so the same "make processing idempotent" rule applies.

## Cleanup

```bash
docker exec broker kafka-consumer-groups --bootstrap-server :9092 --delete --group settlement 2>/dev/null
docker exec broker kafka-topics --bootstrap-server :9092 --delete --topic payments
docker exec broker kafka-topics --bootstrap-server :9092 --delete --topic payments-dlq
```
