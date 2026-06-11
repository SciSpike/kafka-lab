# A2 — "I got the same message twice": at-least-once & idempotent consumers

> 🏫 **Classroom track** · ~45 min · Extends
> [`find-the-flaw.md`](../../04-Implement-Topics-And-Partitions/find-the-flaw.md) questions 2 & 3:
> *"What happens if a consumer goes down and comes back up again?"* and *"…if its partition is
> transferred to another consumer?"* Here we make a consumer reprocess messages and watch the
> duplicates appear.

## The idea

A Kafka consumer tracks its progress by periodically **committing an offset** — a bookmark
saying "I have processed everything up to here." The critical detail: in a normal consumer
the order is

```
1. poll a batch of records
2. process them   (do the work — update a DB, send an email, charge a card…)
3. commit the offset
```

If the consumer **crashes between step 2 and step 3**, the work is done but the bookmark was
never moved. When the consumer (or another consumer in the same group) restarts, it resumes
from the **old** bookmark and **processes those records again**. This is Kafka's default
**at-least-once** delivery: never lose a message, but possibly deliver it more than once.

That is fine *if your processing is idempotent* (doing it twice == doing it once). It is a
bug if it isn't — think "charge the customer", "increment a counter", "send the email".

In this exercise we reproduce the duplicate **deterministically**, then make the processing
idempotent. No Java — a console consumer plays the role of the application, and we model
"processing" by writing each message to a file.

## Objectives

1. Reproduce duplicate delivery caused by a crash before the offset commit.
2. Use `kafka-consumer-groups` to *see* the committed offset lagging behind the work done.
3. Make the consumer idempotent and watch the duplicates disappear.

## Prerequisites

The shared Kafka stack running (`cd docker && docker compose up -d`).

## Setup — a topic and ten orders

Create the topic and produce ten numbered "orders". Note the **`-i`** on `docker exec` — it
forwards standard input so the piped lines reach the producer:

```bash
docker exec broker kafka-topics --bootstrap-server :9092 \
  --create --topic orders --partitions 1 --replication-factor 1

printf 'order-%s\n' $(seq 1 10) | \
  docker exec -i broker kafka-console-producer --bootstrap-server :9092 --topic orders
```

Check that ten records landed:

```bash
docker exec broker kafka-get-offsets --bootstrap-server :9092 --topic orders
# orders:0:10
```

## Step 1 — Process the orders, then "crash" before committing

We run a consumer in the consumer group **`billing`** with **auto-commit turned off**, which
models the worst case: the application did its work but the crash happened before any offset
was committed. It reads all 10 records (our "processing" = writing them to `run1.txt`) and
exits without committing:

```bash
docker run --rm --network docker_kafka_network confluentinc/confluent-local:7.4.1 \
  kafka-console-consumer --bootstrap-server broker:29092 --topic orders \
  --group billing --from-beginning \
  --consumer-property enable.auto.commit=false \
  --max-messages 10 > run1.txt

echo "run 1 processed $(grep -c order- run1.txt) orders"
# run 1 processed 10 orders
```

We have now "processed" all ten orders. Ask the cluster where the `billing` group thinks it
is:

```bash
docker exec broker kafka-consumer-groups --bootstrap-server :9092 --describe --group billing
```

You will see it reports **no committed offset** for the partition (often shown as `-` with a
warning that the group has no active members). The work was done; the bookmark was never
moved. **This is the crash-before-commit gap, made visible.**

## Step 2 — Restart the consumer → duplicates

The consumer (or a replacement in the same group, as in find-the-flaw question 3) comes back.
With no committed offset, it resumes from the beginning and processes all ten **again**:

```bash
docker run --rm --network docker_kafka_network confluentinc/confluent-local:7.4.1 \
  kafka-console-consumer --bootstrap-server broker:29092 --topic orders \
  --group billing --from-beginning \
  --consumer-property enable.auto.commit=false \
  --max-messages 10 > run2.txt

echo "run 2 processed $(grep -c order- run2.txt) orders"
# run 2 processed 10 orders
```

Now reveal the duplicates — every order that appears in **both** runs:

```bash
cat run1.txt run2.txt | sort | uniq -d
```

```
order-1
order-10
order-2
...
order-9
```

Every single order was processed twice. If "processing an order" meant *charging a credit
card*, you just billed every customer twice.

> 🔬 **A more realistic crash window.** Turning auto-commit fully off is the extreme case.
> Real consumers usually commit *periodically* (`auto.commit.interval.ms`, default 5s), so the
> duplicate window is "whatever was processed since the last commit." To feel that, re-run
> Step 1 with `enable.auto.commit=true auto.commit.interval.ms=60000` as two
> `--consumer-property` flags, start it **without** `--max-messages`, watch a few orders print,
> then crash it from another terminal with `docker kill` before the 60-second commit fires.
> The orders processed since the last commit come back on restart.

## Step 3 — Make the consumer idempotent

The records were delivered more than once — that is Kafka working as designed. The fix lives
in **your processing**: make handling the same record twice equivalent to handling it once.

The classic pattern is **dedupe on a stable key**: remember which message ids you have already
processed and skip the repeats. Here is the idea in pure shell — keep a `processed.log` of ids
we've handled, and only "process" an order whose id we have not seen:

```bash
# pretend each line "order-N" carries a unique id N; skip ids we've already handled
process() {            # $1 = file of incoming orders
  touch processed.log
  while read -r order; do
    if grep -qxF "$order" processed.log; then
      echo "skip (already processed): $order"
    else
      echo "PROCESS: $order"          # <-- the real side-effect goes here
      echo "$order" >> processed.log  # <-- record that we did it
    fi
  done < "$1"
}

rm -f processed.log
process run1.txt        # first delivery — all processed
process run2.txt        # redelivery — all skipped
```

Run it: the first delivery processes all ten, the redelivery skips all ten. Same messages
delivered twice, but the **effect** happened once.

## Discussion

- **At-least-once is the default, and it is usually the right default** — losing messages is
  worse than occasionally repeating one. Your job is to make repeats harmless.
- **Where does the "remembered id" really live?** In production it is not a shell file — it's
  a uniqueness constraint or upsert in your database, a deduplication cache, or the
  **transactional outbox** pattern. The id is often a natural key (order id, payment id) or a
  `(topic, partition, offset)` tuple.
- **Commit strategy matters too.** Committing *after* processing (as above) gives at-least-once.
  Committing *before* processing would give at-most-once (you can lose work instead). You
  rarely get "exactly once" for free — Kafka's transactional/exactly-once support narrows the
  window but the consumer's *side effects on the outside world* (charging a card) still need to
  be idempotent.
- **Connect it to find-the-flaw question 3:** the same thing happens during a **rebalance** —
  if a consumer dies after processing but before committing, the partition is reassigned to
  another consumer in the group, which replays from the last commit. The duplicate is identical;
  only the "who" changed. (Exercise A3 triggers exactly that.)

## Cleanup

```bash
rm -f run1.txt run2.txt processed.log
docker exec broker kafka-topics --bootstrap-server :9092 --delete --topic orders
```
