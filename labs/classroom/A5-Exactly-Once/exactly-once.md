# A5 — Exactly-once: transactions and `read_committed`

> 🏫 **Classroom track** · ~40 min · The capstone of the delivery-guarantee arc. [A1](../A1-Crash-Before-Ack/crash-before-ack.md)
> showed how you can **lose** messages; [A2](../A2-Duplicate-Consumption/duplicate-consumption.md)
> and [A3](../A3-Rebalance-Reprocessing/rebalance-reprocessing.md) showed the **duplicates** of
> at-least-once. This lab shows the third option Kafka offers — **exactly-once** — and makes it
> visible. Uses a small provided Java producer, built and run via Docker like the other Java labs.

## The idea

Kafka's exactly-once support (EOS) has two halves:

1. The **idempotent producer** (`enable.idempotence=true`) — a retried send is de-duplicated by the
   broker, so a network hiccup can't turn one record into two.
2. **Transactions** — a producer can write a *batch* of records atomically: either **all** of them
   become visible, or **none** do. It calls `beginTransaction()`, sends records, then either
   `commitTransaction()` or `abortTransaction()`.

The visible half is the consumer's **`isolation.level`**:

- **`read_uncommitted`** (the consumer default) — see every record in the log, including ones from
  transactions that were *aborted*.
- **`read_committed`** — only ever see records from **committed** transactions. Aborted records are
  physically in the log but the consumer skips right over them.

So a `read_committed` consumer acts only on data that "really happened" — work that was rolled back
never reaches it. That's exactly-once for the *consume side* of a pipeline.

The provided `TxnProducer` writes a batch inside a transaction and then commits or aborts it
(flag-controlled), so you can watch the two isolation levels disagree:

```
-Dtopic=payments   -Dcount=5   -Daction=commit|abort   -Dprefix=ok
```

## Objectives

1. Write one **committed** and one **aborted** batch with a transactional producer.
2. See a `read_uncommitted` consumer get **both** batches.
3. See a `read_committed` consumer get **only the committed** one — the aborted batch is invisible.

## Prerequisites

The Kafka stack running (single-broker or the classroom cluster), plus the Docker Maven/Java
toolchain (`maven:3-jdk-11`, `openjdk:11`).

## Step 1 — Build the transactional producer

From this lab's `txn-producer/` directory:

```bash
cd txn-producer
docker run -it --rm -v "$PWD":/work -w /work \
  -v "$HOME/.m2/repository":/root/.m2/repository maven:3-jdk-11 mvn clean package
```

## Step 2 — A topic to write to

```bash
docker exec broker kafka-topics --bootstrap-server :9092 \
  --create --topic payments --partitions 1 --replication-factor 1
```

## Step 3 — Commit a batch, then abort a batch

Write a **committed** batch (`ok-1`…`ok-5`):

```bash
docker run --network docker_kafka_network --rm -v "$PWD:/pwd" -w /pwd openjdk:11 \
  java -Daction=commit -Dprefix=ok -Dcount=5 -jar target/txn-producer-1.0.0-SNAPSHOT.jar
```

```
Transaction: writing 5 record(s) to 'payments' and will COMMIT.
  sent (not yet visible to read_committed): ok-1
  ...
COMMITTED: these records are now visible to every consumer.
```

Now write an **aborted** batch (`bad-1`…`bad-5`) — imagine a batch that failed a validation check and
gets rolled back:

```bash
docker run --network docker_kafka_network --rm -v "$PWD:/pwd" -w /pwd openjdk:11 \
  java -Daction=abort -Dprefix=bad -Dcount=5 -jar target/txn-producer-1.0.0-SNAPSHOT.jar
```

```
ABORTED: read_uncommitted consumers will see these records; read_committed consumers will NOT.
```

Both batches are now physically in the log (the aborted one too, followed by an abort marker):

```bash
docker exec broker kafka-get-offsets --bootstrap-server :9092 --topic payments
# payments:0:12   (5 ok + commit marker + 5 bad + abort marker)
```

## Step 4 — The reveal: two isolation levels, two different answers

Read the topic as a **`read_uncommitted`** consumer — the raw log, including the aborted batch:

```bash
docker exec broker kafka-console-consumer --bootstrap-server :9092 --topic payments \
  --from-beginning --isolation-level read_uncommitted --timeout-ms 6000 | sort
```

```
bad-1
bad-2
bad-3
bad-4
bad-5
ok-1
ok-2
ok-3
ok-4
ok-5
```

Now read it as a **`read_committed`** consumer:

```bash
docker exec broker kafka-console-consumer --bootstrap-server :9092 --topic payments \
  --from-beginning --isolation-level read_committed --timeout-ms 6000 | sort
```

```
ok-1
ok-2
ok-3
ok-4
ok-5
```

Same topic, same offsets — but the `read_committed` consumer **never sees the aborted `bad-*`
batch**. The work that was rolled back simply does not exist as far as it's concerned. That's the
guarantee: a downstream consumer acts only on transactions that committed.

## Discussion

- **This completes the three delivery semantics:**

  | Semantic | How | Lab |
  |----------|-----|-----|
  | at-most-once | `acks=0` / commit before processing | A1 |
  | at-least-once | `acks≥1` / commit after processing — **the default** | A2, A3 |
  | exactly-once | idempotent producer + transactions + `read_committed` | **this lab** |

- **Where EOS shines: read-process-write.** The real power isn't just producing transactionally — a
  consume→process→produce app can put *both* its output records **and** its consumed-offset commit
  into one transaction (`sendOffsetsToTransaction`). Then a crash can't leave output written but the
  offset un-committed (a duplicate) or the offset committed but output missing (a loss). Kafka
  Streams does this for you with one line: `processing.guarantee=exactly_once_v2`.
- **The boundary is Kafka.** EOS is exactly-once *within Kafka*. The moment your processing touches
  the **outside world** — charge a card, send an email, write to a non-transactional DB — that side
  effect still has to be **idempotent**, exactly as you made it in [A2](../A2-Duplicate-Consumption/duplicate-consumption.md).
  EOS shrinks the duplicate window to zero inside Kafka; idempotent design covers everything outside.
- **It isn't free.** Transactions add latency (extra round-trips, markers) and `read_committed`
  consumers read only up to the last *stable* offset. Reach for EOS when correctness demands it
  (money, dedup-critical pipelines), and stick with idempotent at-least-once when it doesn't.

## Cleanup

```bash
docker exec broker kafka-topics --bootstrap-server :9092 --delete --topic payments
```
