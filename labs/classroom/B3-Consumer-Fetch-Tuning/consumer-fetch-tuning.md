# B3 — Consumer fetch tuning: `fetch.min.bytes` and the latency/efficiency trade-off

> 🏫 **Classroom track** · ~30 min · The consumer-side companion to
> [B1](../B1-Producer-Tuning/producer-tuning.md). B1 tuned how the *producer* batches; here we tune
> how the *consumer* fetches — and watch the same throughput-vs-latency tension from the other end.

## The idea

Every time a consumer calls `poll()`, it sends a **fetch** request to the broker. Two settings
control how the broker answers:

- **`fetch.min.bytes`** — the broker waits until it has at least this many bytes to return. Higher
  means fewer, fatter fetches (less overhead, better throughput) — but the consumer may have to
  *wait* for enough data to pile up.
- **`fetch.max.wait.ms`** — the cap on that wait. The broker answers when it has `fetch.min.bytes`
  **or** when `fetch.max.wait.ms` elapses, whichever comes first.

So `fetch.min.bytes` is a latency/efficiency dial:

- **Low `fetch.min.bytes` (default is 1)** → the broker answers immediately with whatever it has →
  **low latency**, but lots of small fetches.
- **High `fetch.min.bytes`** → the broker waits to accumulate a big batch (up to `fetch.max.wait.ms`)
  → **fewer, more efficient fetches**, but every record can be delayed by up to `fetch.max.wait.ms`.

You'll *see* that delay directly.

## Objectives

1. Watch records arrive immediately with the default fetch settings.
2. Watch the same records arrive in delayed **bursts** when `fetch.min.bytes` is high.
3. Reason about when each setting is the right choice.

## Prerequisites

The shared single-broker stack running (`cd docker && docker compose up -d`). Two terminals.

## Setup — a topic and a slow trickle of records

```bash
docker exec broker kafka-topics --bootstrap-server :9092 \
  --create --topic fetch-demo --partitions 1 --replication-factor 1
```

In **terminal 1**, produce one record per second (leave this running for both steps below; restart
it for each step):

```bash
for i in $(seq 1 30); do echo "msg-$i"; sleep 1; done \
  | docker exec -i broker kafka-console-producer --bootstrap-server :9092 --topic fetch-demo
```

A trickle of small records is the case where fetch settings matter most.

## Step 1 — Default fetch: low latency

In **terminal 2**, consume with the default `fetch.min.bytes=1`. We prefix each line with the wall-
clock time it was *received* so the timing is unambiguous (`stdbuf -oL` keeps the output flowing
record-by-record instead of buffering):

```bash
docker exec broker bash -c \
  'stdbuf -oL kafka-console-consumer --bootstrap-server :9092 --topic fetch-demo \
     --consumer-property fetch.min.bytes=1' \
  | while IFS= read -r line; do echo "$(date +%H:%M:%S) recv $line"; done
```

Each record shows up **about one second after the last** — essentially as soon as it's produced:

```
09:37:52 recv msg-1
09:37:53 recv msg-3
09:37:55 recv msg-5
09:37:56 recv msg-6
...
```

Low latency: the broker returns each record the moment it arrives. Stop it with `Ctrl-C`.

## Step 2 — High `fetch.min.bytes`: efficiency at the cost of latency

Restart the producer in terminal 1, then in terminal 2 consume with a large `fetch.min.bytes` and a
4-second `fetch.max.wait.ms`. Now the broker holds the response until it has a megabyte (which, at
one tiny record per second, never happens) **or** 4 seconds pass:

```bash
docker exec broker bash -c \
  'stdbuf -oL kafka-console-consumer --bootstrap-server :9092 --topic fetch-demo \
     --consumer-property fetch.min.bytes=1000000 \
     --consumer-property fetch.max.wait.ms=4000' \
  | while IFS= read -r line; do echo "$(date +%H:%M:%S) recv $line"; done
```

The records now arrive in **~4-second bursts** — everything produced during each window is delivered
together:

```
09:37:18 recv msg-1
09:37:18 recv msg-2
09:37:18 recv msg-3
09:37:18 recv msg-4
09:37:22 recv msg-5      <- 4 seconds later
09:37:22 recv msg-6
09:37:22 recv msg-7
09:37:22 recv msg-8
09:37:26 recv msg-9
...
```

Same producer, same data — but each record waited up to 4 seconds to be delivered. That's the
`fetch.max.wait.ms` cap doing its job. In exchange, the consumer made far fewer fetch requests (one
every 4 s instead of one per record) — cheaper for both client and broker. Stop with `Ctrl-C`.

> 💡 No timestamps needed to *feel* it — if you run the consumer plainly in a terminal (without the
> `| while …` part), you'll watch the records appear on screen in visible clumps every ~4 seconds
> with the high setting, versus a steady trickle with the default.

## Discussion

- **`fetch.min.bytes` is the consumer-side mirror of the producer's `linger.ms`/`batch.size`
  ([B1](../B1-Producer-Tuning/producer-tuning.md)).** Both trade latency for efficiency by batching;
  one batches on the way out, the other on the way in.
- **Pick by workload.** A latency-sensitive consumer (alerting, interactive) wants `fetch.min.bytes`
  low — deliver now. A throughput-oriented consumer (bulk ETL, analytics) wants it higher — fewer,
  fatter fetches use less CPU and network per record, and the extra few milliseconds (or seconds)
  of latency don't matter.
- **`fetch.max.wait.ms` bounds the downside.** Even with a huge `fetch.min.bytes`, you never wait
  longer than this — so on a busy topic where the byte threshold is hit instantly, high
  `fetch.min.bytes` costs almost no latency at all. The pain only shows up on *sparse* topics (which
  is exactly why we used a 1-record-per-second trickle to expose it).
- **Related knobs to mention:** `max.partition.fetch.bytes` / `fetch.max.bytes` cap how much a single
  fetch can return (memory vs. throughput), and `max.poll.records` caps how many records one `poll()`
  hands you (callback to [A4](../A4-Slow-Poll-Eviction/slow-poll-eviction.md), where too many per poll
  caused the eviction).

## Cleanup

```bash
docker exec broker kafka-topics --bootstrap-server :9092 --delete --topic fetch-demo
```
