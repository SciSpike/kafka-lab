# A4 — The slow-poll eviction: `max.poll.interval.ms` and the endless rebalance

> 🏫 **Classroom track** · ~40 min · This is the one classroom lab that uses a small **Java**
> program (provided for you), built and run through Docker exactly like the
> [online publisher/subscriber lab](../../02-Publish-And-Subscribe/instructions.md). Why Java here?
> Faithfully triggering *and fixing* this failure needs a real delay inside the consumer's poll
> loop — something the command-line tools can't express. You don't need to write any Java; just
> read it, build it, and run it. Pair up if Java isn't your language.

## The idea

A Kafka consumer must call `poll()` regularly. Between polls it processes the records the previous
poll returned. Kafka enforces this with **`max.poll.interval.ms`**: if you don't call `poll()` again
within that deadline, the broker assumes your consumer is dead or stuck, **kicks it out of the
group**, and rebalances its partitions to someone else.

The trap: the time between polls is roughly

```
time between polls  ≈  max.poll.records  ×  (processing time per record)
```

So if each record is slow to process and `max.poll.records` is large, one batch can blow past the
deadline. When that happens:

1. the consumer is evicted mid-batch,
2. its commit **fails** (it's no longer in the group),
3. the batch is redelivered and reprocessed from the same offset,
4. …which is *also* too slow, so it's evicted again — **forever, making zero progress.**

This is one of the most common and most baffling Kafka consumer bugs ("it just keeps rebalancing
and never moves"). Here you'll make it happen, then fix it.

The provided program, `slow-consumer`, "processes" each record by sleeping a configurable number of
milliseconds, then commits the batch. Every relevant knob is a command-line flag, so you can break
it and fix it without editing code:

| Flag | Meaning | Default |
|------|---------|---------|
| `-DprocessMs=1000` | time to "process" one record (ms) | 1000 |
| `-DmaxPollRecords=10` | `max.poll.records` | 10 |
| `-DmaxPollIntervalMs=5000` | `max.poll.interval.ms` (the deadline) | 5000 |
| `-Dtopic=poll-test` | topic to consume | poll-test |

The defaults are deliberately **broken** (10 records × 1000 ms = 10 s of work, but a 5 s deadline).

## Objectives

1. Watch a consumer get evicted because it processes a batch too slowly, and see the exact error.
2. See that it makes **no progress** — it reprocesses the same offset forever.
3. Fix it two ways and watch it drain cleanly.

## Prerequisites

The shared single-broker stack running (`cd docker && docker compose up -d`), plus the Docker-based
Maven/Java toolchain used by the online labs (`maven:3-jdk-11` and `openjdk:11` images).

## Step 1 — Build the consumer

From this lab's `slow-consumer/` directory:

```bash
cd slow-consumer
docker run -it --rm -v "$PWD":/work -w /work \
  -v "$HOME/.m2/repository":/root/.m2/repository maven:3-jdk-11 mvn clean package
```

> On Windows, replace `$PWD`/`$HOME` with the appropriate paths if your shell doesn't expand them
> (same note as the online Java labs).

This produces `target/slow-consumer-1.0.0-SNAPSHOT.jar`.

## Step 2 — Make a backlog

```bash
docker exec broker kafka-topics --bootstrap-server :9092 \
  --create --topic poll-test --partitions 1 --replication-factor 1
docker exec broker kafka-producer-perf-test --topic poll-test \
  --num-records 60 --record-size 100 --throughput -1 \
  --producer-props bootstrap.servers=:9092 acks=1
```

## Step 3 — Run it broken (watch the eviction)

Run with the defaults — 10 records at 1 s each is 10 s of work, but the deadline is 5 s:

```bash
docker run --network docker_kafka_network --rm -it -v "$PWD:/pwd" -w /pwd openjdk:11 \
  java -DprocessMs=1000 -DmaxPollRecords=10 -DmaxPollIntervalMs=5000 -Dtopic=poll-test \
  -jar target/slow-consumer-1.0.0-SNAPSHOT.jar
```

You'll see it predict the failure, then live it out:

```
  worst-case batch took = 10 records x 1000 ms = 10000 ms
  >>> batch time EXCEEDS the deadline: expect eviction + commit failures.
>> REBALANCE: partitions assigned [poll-test-0]
Polled 10 records (from offset 0); processing (1000 ms each)...
  !! COMMIT FAILED -- this consumer was kicked out of the group because
     processing the batch took longer than max.poll.interval.ms.
     Offset commit cannot be completed since the consumer is not part of an active group ...
     => this batch (from offset 0) will be redelivered and redone. Total committed is still 0.
>> REBALANCE: partitions revoked  [poll-test-0]
>> REBALANCE: partitions assigned [poll-test-0]
Polled 10 records (from offset 0); processing (1000 ms each)...      <- offset 0 AGAIN
  !! COMMIT FAILED -- ...
```

Three things to point out, all visible on screen:

- The **commit fails** with *"the consumer is not part of an active group … kicked out of the
  group."* That's the eviction.
- It **rebalances** every cycle (partitions revoked then reassigned).
- It re-polls **from offset 0 every time** — same batch, over and over. Confirm from another
  terminal that it never commits anything:

  ```bash
  docker exec broker kafka-consumer-groups --bootstrap-server :9092 --describe --group slow-poll-group
  # CURRENT-OFFSET is empty/0 and LAG stays at 60 — zero progress
  ```

Stop it with `Ctrl-C`.

## Step 4 — Fix it (smaller batches)

Kafka's own error message tells you the fix: *"increase max.poll.interval.ms or reduce … max.poll.records."*
Try the second — make each batch small enough to finish inside the 5 s deadline (3 records × 1 s = 3 s):

```bash
docker run --network docker_kafka_network --rm -it -v "$PWD:/pwd" -w /pwd openjdk:11 \
  java -DprocessMs=1000 -DmaxPollRecords=3 -DmaxPollIntervalMs=5000 -Dtopic=poll-test \
  -jar target/slow-consumer-1.0.0-SNAPSHOT.jar
```

Now it works — every batch commits and the offset climbs:

```
  >>> batch time is within the deadline: expect smooth progress.
Polled 3 records (from offset 0); processing (1000 ms each)...
  committed OK -- progress! total processed so far: 3
Polled 3 records (from offset 3); processing (1000 ms each)...
  committed OK -- progress! total processed so far: 6
Polled 3 records (from offset 6); processing (1000 ms each)...
  committed OK -- progress! total processed so far: 9
...
```

Check the group from another terminal and watch `CURRENT-OFFSET` rise and `LAG` fall toward 0. Stop
with `Ctrl-C`.

> **The other fix:** instead of shrinking the batch, give yourself more time. Stop, reset the group
> (`docker exec broker kafka-consumer-groups --bootstrap-server :9092 --delete --group slow-poll-group`),
> and run the original 10-record batch but with a generous deadline:
> `-DmaxPollRecords=10 -DmaxPollIntervalMs=60000`. It also makes progress — because now even a 10 s
> batch finishes well inside the 60 s deadline.

## Discussion

- **`max.poll.interval.ms` is a liveness check, not a timeout on a single record.** It asks "are you
  still calling `poll()`?" You satisfy it by keeping each *batch* of work shorter than the deadline.
- **The two config levers trade off against each other.** Smaller `max.poll.records` = more frequent
  polls = safer, but more overhead. Larger `max.poll.interval.ms` = tolerate longer batches, but a
  genuinely hung consumer takes longer to be detected and replaced. Tune them together to your real
  per-record processing time.
- **Sometimes the right fix is neither** — it's to make processing *faster* or move slow work off the
  poll thread (process asynchronously, batch your database writes, call slow services in parallel).
  If one record takes 30 seconds, no `max.poll` setting saves you.
- **This is at-least-once gone wrong** (connects to [A2](../A2-Duplicate-Consumption/duplicate-consumption.md)
  and [A3](../A3-Rebalance-Reprocessing/rebalance-reprocessing.md)): the endless reprocessing is the
  same "work done but never committed" problem, here caused by being too slow rather than by a crash.
  Every redelivery is also a duplicate — so idempotent processing matters here too.

## Cleanup

```bash
docker exec broker kafka-consumer-groups --bootstrap-server :9092 --delete --group slow-poll-group 2>/dev/null
docker exec broker kafka-topics --bootstrap-server :9092 --delete --topic poll-test
```
