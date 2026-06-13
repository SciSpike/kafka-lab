# B1 — Producer throughput vs latency: batching, compression, acks

> 🏫 **Classroom track** · ~40 min · A measurable extension of the online
> [`performance.md`](../../02-Publish-And-Subscribe/performance.md) lab. There the advice was
> "read the docs and play"; here you will actually **measure** the effect of each knob — with
> no Java to rebuild.

## The idea

A Kafka producer almost never sends one record per network round-trip — that would be slow.
Instead it **batches** records together and ships them in bigger, more efficient chunks. The
key knobs that control this tradeoff:

| Setting | What it does | Turn it up and… |
| ------- | ------------ | --------------- |
| `batch.size` | max bytes per per-partition batch | bigger batches → better throughput, more memory |
| `linger.ms` | how long to *wait* for a batch to fill before sending | more batching → higher throughput, **higher latency** |
| `compression.type` | `none`/`lz4`/`snappy`/`zstd`/`gzip` | less network/disk, more CPU |
| `acks` | how much confirmation to wait for (see [A1](../A1-Crash-Before-Ack/crash-before-ack.md)) | safer, but slower |

The central tension: **batching trades latency for throughput.** `linger.ms` is the purest
example — you are literally telling the producer to *wait* (adding latency) so it can send
more per trip (raising throughput).

We measure with `kafka-producer-perf-test`, a standard Kafka tool that hammers a topic and
prints throughput and latency percentiles. Every parameter is a command-line flag, so there is
nothing to compile.

## Objectives

1. Read the throughput/latency report `kafka-producer-perf-test` produces.
2. Sweep `batch.size`, `linger.ms`, `compression.type`, and `acks`, recording the effect.
3. Explain the throughput-vs-latency tradeoff from your own numbers.

## Prerequisites

The **classroom cluster** running (start it once at the beginning of the course and leave it up — see [classroom-labs.md](../classroom-labs.md)).

## Setup — a topic to benchmark

```bash
docker exec broker kafka-topics --bootstrap-server :9092 \
  --create --topic perftest --partitions 1 --replication-factor 1
```

## Step 1 — A baseline run

```bash
docker exec broker kafka-producer-perf-test \
  --topic perftest --num-records 300000 --record-size 200 --throughput -1 \
  --producer-props bootstrap.servers=:9092 acks=1 linger.ms=0 batch.size=16384
```

`--throughput -1` means "go as fast as you can"; `--record-size 200` is bytes per record. The
final line is the report:

```
300000 records sent, 339366.5 records/sec (64.73 MB/sec), 0.61 ms avg latency, 152.00 ms max latency, 1 ms 50th, 1 ms 95th, 3 ms 99th, 6 ms 99.9th.
```

Read it as: **records/sec** and **MB/sec** are throughput (higher = better); the **ms**
figures are latency, including the **50th/95th/99th/99.9th percentiles** (lower = better). The
percentiles matter more than the average — the 99.9th tells you about your worst-served
requests.

> Your exact numbers will differ from the sample — they depend on your laptop. What matters is
> the **direction** each knob moves them, and you compare runs **on your own machine**.

## Step 2 — Sweep the knobs

Run the command again, changing **one thing at a time** in `--producer-props`, and record the
report each time. Suggested sweep:

```bash
# (a) more batching: bigger batches + wait to fill them
docker exec broker kafka-producer-perf-test \
  --topic perftest --num-records 300000 --record-size 200 --throughput -1 \
  --producer-props bootstrap.servers=:9092 acks=1 linger.ms=50 batch.size=131072

# (b) add compression on top of (a)
docker exec broker kafka-producer-perf-test \
  --topic perftest --num-records 300000 --record-size 200 --throughput -1 \
  --producer-props bootstrap.servers=:9092 acks=1 linger.ms=50 batch.size=131072 compression.type=lz4

# (c) the safety dial: acks=0 vs acks=1 vs acks=all
docker exec broker kafka-producer-perf-test \
  --topic perftest --num-records 300000 --record-size 200 --throughput -1 \
  --producer-props bootstrap.servers=:9092 acks=0 linger.ms=0 batch.size=16384

docker exec broker kafka-producer-perf-test \
  --topic perftest --num-records 300000 --record-size 200 --throughput -1 \
  --producer-props bootstrap.servers=:9092 acks=all linger.ms=0 batch.size=16384
```

Fill in a table as you go:

| Run | `acks` | `linger.ms` | `batch.size` | `compression` | records/sec | MB/sec | 99th latency |
| --- | ------ | ----------- | ------------ | ------------- | ----------- | ------ | ------------ |
| baseline | 1 | 0   | 16384  | none | | | |
| (a)      | 1 | 50  | 131072 | none | | | |
| (b)      | 1 | 50  | 131072 | lz4  | | | |
| (c-0)    | 0 | 0   | 16384  | none | | | |
| (c-all)  | all | 0 | 16384  | none | | | |

## Discussion

- **`linger.ms` is the throughput-vs-latency dial in its purest form.** Waiting to fill a batch
  lifts throughput on a busy topic but adds latency to every record. On a fast local broker that
  is *already* keeping up, you may see the throughput barely move while the **tail latency (99th,
  99.9th) clearly rises** — a perfect illustration that batching is not free.
- **Compression** trades CPU for bytes-on-the-wire. Its payoff grows with bigger batches (more to
  compress) and with compressible payloads — try a larger `--record-size` and see the MB/sec gap
  widen.
- **`acks` costs throughput for safety** — the same dial you'll crash a producer on in [A1](../A1-Crash-Before-Ack/crash-before-ack.md).
  Because `perftest` is a **single-replica (RF=1)** topic, `acks=all` ≈ `acks=1` here — there are no
  followers to wait for. Re-create it with `--replication-factor 3` on the classroom cluster and the
  gap becomes real: `acks=all` then waits for the round-trip to the followers (see C1).
- **Local caveat:** absolute numbers here are not production figures — on an RF=1 topic there's no
  replication round-trip, and the clients and brokers all share your laptop's CPU. Treat this as
  learning the *shape* of the tradeoffs, not benchmarking your future cluster.

> 💡 **Want to feel batching properly?** Re-run the sweep with `--throughput 20000` (rate-limited
> instead of flat-out). When the producer is *not* saturated, `linger.ms` has room to actually
> accumulate batches, and its effect on both throughput and latency becomes much clearer than in
> the wide-open `--throughput -1` runs.

### The consumer side

The matching consumer knobs (`fetch.min.bytes`, `max.partition.fetch.bytes`, `max.poll.records`)
get the same measurable treatment in exercise **B3**, using `kafka-consumer-perf-test`.

## Cleanup

```bash
docker exec broker kafka-topics --bootstrap-server :9092 --delete --topic perftest
```
