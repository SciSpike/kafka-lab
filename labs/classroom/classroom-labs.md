# Classroom track — instructor-led exercises

> 🏫 **Classroom track.** These exercises are **not** part of the self-paced 2×3h online
> course (that one lives in [`../labs.md`](../labs.md)). They are designed for the
> instructor-led, 2-full-day classroom format, where there is time to trigger failures,
> measure things, and discuss results together.

## Introduction

The online labs are deliberately "follow the instructions and it works". These classroom
exercises do the opposite: they **deliberately break things** so you can see — with your
own eyes — the failure modes that bite real Kafka projects, and then measure the effect of
the knobs you can turn to fix them.

A guiding principle of this track: **students never have to develop code.** Most exercises are
driven entirely from Kafka's own command-line tools (which ship inside the broker container),
plus a bit of shell — so the lessons are the same whether your daily language is Java, Python,
Go, C#, or something else, and you can re-implement any pattern in your own stack afterwards.
Where the command-line tools genuinely can't express a scenario (only [A4](A4-Slow-Poll-Eviction/slow-poll-eviction.md),
which needs a real delay inside the consumer's poll loop), we **provide** ready-to-run Java that
you build and run through Docker — exactly like the online publisher/subscriber lab. You read and
run it; you never write it.

Every exercise reuses the **same Docker stack** as the online labs. If it is not already
running, start it from the repository's `docker` directory:

```bash
cd docker
docker compose up -d
```

A few conventions used throughout this track:

- The Kafka broker container is named **`broker`**, so commands use `docker exec broker …`
  directly (no need to look up a container id). When piping data **into** a command you
  need `docker exec -i broker …` (the `-i` forwards standard input).
- When an exercise needs a **second, disposable client** (a producer or consumer we can
  crash on purpose), we run it as a throwaway container on the cluster's network using the
  image that is **already on your machine** (`confluentinc/confluent-local:7.4.1`), so there
  is nothing extra to download:

  ```bash
  docker run --rm --network docker_kafka_network confluentinc/confluent-local:7.4.1 <kafka-tool> …
  ```

- `docker stop <name>` sends **SIGTERM** (a *graceful* shutdown — the client flushes).
  `docker kill <name>` sends **SIGKILL** (a *hard crash* — no flush). We use the difference
  between these two deliberately.

## Outline

### Theme A — Delivery guarantees & crash failures

These pick up exactly where [`../04-Implement-Topics-And-Partitions/find-the-flaw.md`](../04-Implement-Topics-And-Partitions/find-the-flaw.md)
leaves off, but instead of *reasoning* about the failures you will *trigger* them.

- ✅ [A1 — Crash before the ack: acks and lost writes](A1-Crash-Before-Ack/crash-before-ack.md)
  *(answers find-the-flaw question 1)*
- ✅ [A2 — "I got the same message twice": at-least-once & idempotent consumers](A2-Duplicate-Consumption/duplicate-consumption.md)
  *(answers find-the-flaw question 2)*
- ✅ [A3 — Rebalancing: in-flight reprocessing when a consumer dies](A3-Rebalance-Reprocessing/rebalance-reprocessing.md)
  *(answers find-the-flaw question 3)*
- ✅ [A4 — The slow-poll eviction: `max.poll.interval.ms` and the endless rebalance](A4-Slow-Poll-Eviction/slow-poll-eviction.md)
  *(uses a small provided Java program, built/run via Docker like the online labs — a real
  `sleep` in the poll loop triggers the eviction deterministically on any OS)*

### Theme B — Performance tuning

A measurable extension of the online [`../02-Publish-And-Subscribe/performance.md`](../02-Publish-And-Subscribe/performance.md)
lab — no Java rebuilds, just sweep flags and record the numbers.

- ✅ [B1 — Producer throughput vs latency: batching, compression, acks](B1-Producer-Tuning/producer-tuning.md)
- ✅ [B2 — Partitions & consumer parallelism: where adding consumers stops helping](B2-Partitions-And-Parallelism/partitions-and-parallelism.md)
- ✅ [B3 — Consumer fetch tuning: `fetch.min.bytes` and the latency/efficiency tradeoff](B3-Consumer-Fetch-Tuning/consumer-fetch-tuning.md)

### Theme C — Richer real-world scenarios

- ✅ [C1 — Multi-broker cluster: replication, ISR, `acks=all`, killing a broker](C1-Multi-Broker-Cluster/multi-broker-cluster.md)
- ✅ [C2 — Consumer lag & backpressure: when consumers can't keep up](C2-Consumer-Lag/consumer-lag.md)
- ✅ [C3 — Ordering & keys: order is only guaranteed within a partition](C3-Ordering-And-Keys/ordering-and-keys.md)
- ✅ [C4 — Log compaction & tombstones: a topic as a changelog of state](C4-Log-Compaction/log-compaction.md)
- ✅ [C5 — Poison pill, dead-letter topics & offset reset](C5-Poison-Pill-And-DLQ/poison-pill-and-dlq.md)

> ✅ = ready to run. All twelve exercises are written and tested end-to-end.
