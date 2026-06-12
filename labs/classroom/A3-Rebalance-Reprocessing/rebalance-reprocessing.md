# A3 — Rebalancing: in-flight reprocessing when a consumer dies

> 🏫 **Classroom track** · ~45 min · The sequel to
> [A2](../A2-Duplicate-Consumption/duplicate-consumption.md) and the answer to
> [`find-the-flaw.md`](../../04-Implement-Topics-And-Partitions/find-the-flaw.md) question 3:
> *"What happens if a consumer goes down and its partition is transferred to another consumer?"*
> In A2 one consumer reprocessed its own messages. Here a **different** consumer inherits the
> dead one's partitions — and redoes its uncommitted work.

## The idea

When several consumers share a **consumer group**, Kafka divides the topic's partitions among
them — each partition is owned by exactly one consumer at a time. Whenever the group's
membership changes (a consumer **joins** or **leaves**), Kafka triggers a **rebalance**: it
re-divides the partitions among the current members.

A rebalance is normal and necessary — it's how Kafka scales consumers out and recovers from
failures. But it has a sharp edge: when a partition moves from a dead consumer to a new owner,
the new owner resumes from the **last committed offset** for that partition. Anything the dead
consumer processed but hadn't committed gets **processed again** — now by a different machine.

This exercise has two parts: first *see* partitions move between consumers, then watch a crash
cause one consumer to redo another's work.

We model two "worker" services with two console consumers in the same group, and "processing"
is again just writing each record to a file.

## Objectives

1. Watch Kafka split partitions across consumers and re-split them on join/leave.
2. See the difference between a **graceful leave** (instant rebalance) and a **crash** (slow).
3. Confirm that a dead consumer's uncommitted work is reprocessed by the consumer that inherits
   its partitions.

## Prerequisites

The **classroom cluster** running (start it once at the beginning of the course and leave it up — see [classroom-labs.md](../classroom-labs.md)). You'll use **three
terminals**: two for the worker consumers, one for inspecting the group.

## Setup — a 4-partition topic with 40 tasks

Multiple partitions are what make sharing (and reassignment) possible:

```bash
docker exec broker kafka-topics --bootstrap-server :9092 \
  --create --topic tasks --partitions 4 --replication-factor 1

# 40 tasks, keyed 0..39 so they spread across the 4 partitions (note the -i!)
for i in $(seq 0 39); do printf '%s:task-%s\n' "$i" "$i"; done | \
  docker exec -i broker kafka-console-producer --bootstrap-server :9092 \
  --topic tasks --property parse.key=true --property key.separator=:
```

## Part 1 — Watch partitions move

**Terminal 1 — Worker A.** Start a consumer in the group `workers`:

```bash
docker run --rm --name worker-a --network docker_kafka_network confluentinc/confluent-local:7.4.1 \
  kafka-console-consumer --bootstrap-server broker:29092 --topic tasks \
  --group workers --from-beginning
```

**Terminal 3 — inspector.** With only Worker A in the group, it owns **all four** partitions.
The last column is the consumer that owns each partition:

```bash
docker exec broker kafka-consumer-groups --bootstrap-server :9092 --describe --group workers \
  | awk 'NR==1 || /tasks/'
```

```
TOPIC  PARTITION  ...  CONSUMER-ID
tasks  0          ...  console-consumer-ad11...
tasks  1          ...  console-consumer-ad11...
tasks  2          ...  console-consumer-ad11...
tasks  3          ...  console-consumer-ad11...      <- one consumer owns all 4
```

**Terminal 2 — Worker B.** Now start a second consumer in the **same group**:

```bash
docker run --rm --name worker-b --network docker_kafka_network confluentinc/confluent-local:7.4.1 \
  kafka-console-consumer --bootstrap-server broker:29092 --topic tasks \
  --group workers --from-beginning
```

Re-run the inspector command in Terminal 3. The four partitions are now **split across two
consumer-ids** — two each. Kafka rebalanced the moment Worker B joined:

```bash
docker exec broker kafka-consumer-groups --bootstrap-server :9092 --describe --group workers \
  | awk '/tasks/ {print $7}' | sort | uniq -c
#   2 console-consumer-90d6...   (Worker B)
#   2 console-consumer-ad11...   (Worker A)
```

Now stop Worker B **gracefully** (in Terminal 2 press `Ctrl-C`, or from another terminal run
`docker stop worker-b`). A graceful stop sends a clean *LeaveGroup*, so the rebalance is
**immediate**. Re-run the inspector — Worker A owns all four partitions again within a few
seconds:

```bash
docker exec broker kafka-consumer-groups --bootstrap-server :9092 --describe --group workers \
  | awk '/tasks/ {print $7}' | sort | uniq -c
#   4 console-consumer-ad11...   (Worker A has them all back)
```

> 🔬 **Graceful leave vs. crash.** A graceful `Ctrl-C` / `docker stop` tells the group "I'm
> leaving" and rebalances instantly. A **crash** (`docker kill`) says nothing — the group only
> notices when the dead consumer misses heartbeats for `session.timeout.ms` (**45 seconds** by
> default!). Try it: `docker kill worker-b` and watch the inspector; the partitions don't move
> for the better part of a minute. In production this is the gap where a crashed consumer's
> partitions sit *unprocessed* until the group gives up on it — one reason people tune
> `session.timeout.ms` down.

Stop Worker A as well before continuing (`Ctrl-C` / `docker stop worker-a`).

## Part 2 — A crash makes another worker redo the work

Now the failure that bites real systems. Reset the group so we start clean:

```bash
docker exec broker kafka-consumer-groups --bootstrap-server :9092 --delete --group workers
```

**Worker A processes everything, then crashes before committing.** We use `enable.auto.commit=false`
to guarantee the crash lands before any commit (the realistic "crashed mid-batch" case), and
`--max-messages 40` so it reads all the tasks and exits — standing in for a worker that did its
job and then died:

```bash
docker run --rm --network docker_kafka_network confluentinc/confluent-local:7.4.1 \
  kafka-console-consumer --bootstrap-server broker:29092 --topic tasks \
  --group workers --from-beginning --consumer-property enable.auto.commit=false \
  --max-messages 40 > workerA.txt

echo "Worker A processed $(grep -c task- workerA.txt) tasks"
```

Worker A did all 40 tasks — but committed nothing. Confirm the group has no recorded progress:

```bash
docker exec broker kafka-consumer-groups --bootstrap-server :9092 --describe --group workers \
  | awk '/tasks/ {print $1, $2, $4}'      # CURRENT-OFFSET column shows "-"
```

**Worker B inherits the partitions and redoes the work.** Because the group never committed, the
new owner starts each partition from the beginning:

```bash
docker run --rm --network docker_kafka_network confluentinc/confluent-local:7.4.1 \
  kafka-console-consumer --bootstrap-server broker:29092 --topic tasks \
  --group workers --from-beginning --consumer-property enable.auto.commit=false \
  --max-messages 40 > workerB.txt

echo "Worker B processed $(grep -c task- workerB.txt) tasks"
```

Now reveal how many tasks were handled by **both** workers:

```bash
comm -12 <(grep -o 'task-[0-9]*' workerA.txt | sort -u) \
         <(grep -o 'task-[0-9]*' workerB.txt | sort -u) | wc -l
# 40
```

Every one of the 40 tasks was processed twice, by two different workers. If "processing a task"
meant shipping an order or sending a notification, every customer got served twice — and the
second time by a *different* server, so a naive "I already did this one" check in Worker A's
local memory wouldn't have caught it.

## Discussion

- **This is the same root cause as [A2](../A2-Duplicate-Consumption/duplicate-consumption.md)**
  — work done but not committed — but triggered by a **rebalance** and landing on a **different
  consumer**. That second point matters: any deduplication has to be *shared* (a database, a
  cache), not in one process's memory.
- **Rebalances are not rare or exceptional.** They happen on every deploy, scale-up, scale-down,
  crash, and even on a consumer that's too slow (see A4). Your processing must be safe across
  them, which means **idempotent processing** plus a sensible **commit strategy** — exactly the
  fix from A2.
- **Had Worker A committed as it went**, only the records processed since its *last* commit would
  have been redone — the duplicate window shrinks to "one commit interval", but it never reaches
  zero with at-least-once. That residual window is why the outside-world side effect still has to
  be idempotent.
- **Tuning note:** `session.timeout.ms` trades crash-detection speed against false alarms. Too
  high and crashed consumers' partitions stall (Part 1); too low and a brief GC pause gets a
  healthy consumer kicked out, causing a needless rebalance (and more reprocessing). Modern
  Kafka's *cooperative* rebalancing reduces the disruption but doesn't remove the duplicate risk.

## Cleanup

```bash
rm -f workerA.txt workerB.txt
docker exec broker kafka-consumer-groups --bootstrap-server :9092 --delete --group workers 2>/dev/null
docker exec broker kafka-topics --bootstrap-server :9092 --delete --topic tasks
```
