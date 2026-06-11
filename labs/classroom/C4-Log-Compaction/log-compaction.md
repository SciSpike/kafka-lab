# C4 — Log compaction & tombstones: a topic as a changelog of state

> 🏫 **Classroom track** · ~40 min · So far every topic has been an append-only *stream of events*.
> This lab introduces the other way to use a topic: as a **changelog of current state**, where Kafka
> keeps only the latest value per key. This is the model behind Kafka Streams' state stores (the
> `*-changelog` topics you saw in the [streaming labs](../../06-Streaming/wordcount-kafka-streaming.md))
> and behind connectors that mirror a database table.

## The idea

A normal topic keeps everything until it ages out by time or size (`cleanup.policy=delete`). A
**compacted** topic (`cleanup.policy=compact`) is different: a background "log cleaner" periodically
throws away **superseded** records, keeping only the **most recent value for each key**.

So if you write:

```
sku1 → price-10
sku1 → price-11
sku1 → price-12
```

after compaction only `sku1 → price-12` remains. The topic stops being "the history of price
changes" and becomes "the current price of every sku" — a table, keyed by key.

There's one more piece: to *delete* a key you write a **tombstone** — a record with the key and a
**null value**. After compaction, the tombstone (and the key's old values) are removed entirely.

## Objectives

1. Create a compacted topic and watch old values for a key disappear, leaving only the latest.
2. Delete a key with a tombstone (null value).
3. Understand when "topic as table" is the right model.

## A note on timing (read this)

Compaction is **asynchronous and best-effort**. The cleaner only rewrites **closed** log segments,
never the one currently being written, and it runs on a periodic cycle. So compaction is not
instant — you make it happen by producing a bit more data (to roll the active segment closed) and
then re-reading. We create the topic with **aggressive** settings so this happens in seconds rather
than hours; in production the defaults are far more relaxed. **Expect to re-run the consume command
a few times before you see the collapse.**

## Prerequisites

The shared single-broker stack running (`cd docker && docker compose up -d`).

## Step 1 — A compacted topic

```bash
docker exec broker kafka-topics --bootstrap-server :9092 \
  --create --topic catalog --partitions 1 --replication-factor 1 \
  --config cleanup.policy=compact \
  --config min.cleanable.dirty.ratio=0.001 \
  --config segment.ms=100 \
  --config min.compaction.lag.ms=0 \
  --config delete.retention.ms=100 \
  --config max.compaction.lag.ms=100
```

Those extra configs just make the cleaner eager (small segments, compact as soon as possible) so the
lab is watchable. Now write several values for two keys:

```bash
printf '%s\n' 'sku1:price-10' 'sku2:price-20' 'sku1:price-11' 'sku1:price-12' 'sku2:price-21' \
  | docker exec -i broker kafka-console-producer --bootstrap-server :9092 --topic catalog \
    --property parse.key=true --property key.separator=:
```

Right now all five records are still there (nothing has been compacted yet):

```bash
docker exec broker kafka-get-offsets --bootstrap-server :9092 --topic catalog
# catalog:0:5
```

## Step 2 — Watch the old values disappear

Compaction only touches **closed** segments, so produce a few more records to roll the active
segment, then read the topic back. Repeat this a couple of times if needed:

```bash
# nudge the cleaner: write a few unrelated records to roll the segment
printf '%s\n' 'sku3:price-30' 'sku3:price-31' 'sku3:price-32' \
  | docker exec -i broker kafka-console-producer --bootstrap-server :9092 --topic catalog \
    --property parse.key=true --property key.separator=:

# read everything back, newest-wins
docker exec broker kafka-console-consumer --bootstrap-server :9092 --topic catalog \
  --from-beginning --property print.key=true --timeout-ms 5000
```

Within a few seconds (and maybe one repeat of the nudge), `sku1`'s three values collapse to just the
latest, and `sku2`'s two collapse to one:

```
sku1   price-12      <- only the latest survives (price-10, price-11 are gone)
sku2   price-21      <- only the latest survives
sku3   price-30
sku3   price-31
sku3   price-32      <- sku3 hasn't compacted yet (still in a recent segment)
```

The log-end offset keeps climbing (the *positions* are never reused), but the **content** is now
"one value per key". Keep nudging and re-reading and `sku3` collapses to `price-32` too. **This is
the topic as a table: every key maps to its current value.**

## Step 3 — Delete a key with a tombstone

To remove a key entirely, write a record with that key and a **null value**. The console producer
sends null when the value matches its `null.marker`:

```bash
printf '%s\n' 'sku1:NULL' \
  | docker exec -i broker kafka-console-producer --bootstrap-server :9092 --topic catalog \
    --property parse.key=true --property key.separator=: --property null.marker=NULL
```

Nudge and re-read a few times (the tombstone needs a compaction cycle to take effect):

```bash
printf '%s\n' 'sku9:tick-1' 'sku9:tick-2' 'sku9:tick-3' \
  | docker exec -i broker kafka-console-producer --bootstrap-server :9092 --topic catalog \
    --property parse.key=true --property key.separator=:

docker exec broker kafka-console-consumer --bootstrap-server :9092 --topic catalog \
  --from-beginning --property print.key=true --timeout-ms 5000
```

After compaction, **`sku1` is gone completely** — not just its old values, but the key itself. A
consumer rebuilding state from this topic will never see `sku1`, exactly as if a row were deleted
from a table. (A brief window exists where the tombstone itself is still visible — `delete.retention.ms`
controls how long — so downstream consumers get a chance to observe the delete before it's cleaned up.)

## Discussion

- **Two cleanup policies, two mental models.** `delete` (the default) = *event stream*, keep history
  for a window then drop the oldest — good for "what happened". `compact` = *current-state table*,
  keep the latest per key forever — good for "what is true now". You can even combine them
  (`compact,delete`).
- **This powers a lot of Kafka.** Kafka Streams state stores are backed by compacted changelog
  topics (you saw `Counts-changelog` in the wordcount lab); `__consumer_offsets` is compacted;
  database-mirroring connectors rely on it. The key is always the entity id; the value is its latest
  state; a tombstone is a delete.
- **Key design is everything here** — same lesson as [C3](../C3-Ordering-And-Keys/ordering-and-keys.md).
  Compaction is *per key*, so a record with no key (null key) can't be compacted meaningfully. Pick
  the key to be the identity of the thing whose state you're tracking.
- **Compaction is not a retention guarantee or a quota.** It bounds the log to "one record per live
  key" *eventually*, but it's asynchronous and never compacts the active segment — never rely on it
  for "this old value is definitely gone right now".

## Cleanup

```bash
docker exec broker kafka-topics --bootstrap-server :9092 --delete --topic catalog
```
