# Fault-tolerant state: the heartbeat monitor in Kafka Streams

In the [topics & partitions implementation lab](../04-Implement-Topics-And-Partitions/topics-and-partitions.md)
you ran a plain-Java `device-monitor` that watches device heartbeats and raises an alarm when a
device goes quiet. It has a flaw — one of the ones [`find-the-flaw.md`](../04-Implement-Topics-And-Partitions/find-the-flaw.md)
hints at:

> Its "when did I last hear from each device?" state lives in an in-memory `HashMap`. **Crash it and
> restart it, and that map is empty** — the monitor forgets every device it was watching and can no
> longer tell that a silent one has gone offline. It only "recovers" a device once it happens to send
> another heartbeat.

This lab re-implements the same monitor in **Kafka Streams** and shows the flaw simply disappear,
because a Streams app keeps its state in a **fault-tolerant state store** that survives a crash.

## The idea

A Kafka Streams app can keep local state (counts, last-seen timestamps, joins, …) in a **state
store**. The trick that makes it fault-tolerant: every change to the store is also written to a
**changelog topic** in Kafka — a compacted topic (exactly the "topic as a table" model from the
[log-compaction lab](../classroom/C4-Log-Compaction/log-compaction.md)). So the durable copy of the
state lives *in Kafka*, not on the app's local disk.

When the app restarts — even on a brand-new machine with an empty disk — Streams **restores the
store from the changelog** before it resumes processing. The app picks up exactly where it left off.

Our monitor keeps two such stores: `last-seen-store` (device → last heartbeat time) and
`status-store` (device → ONLINE/OFFLINE). The provided `HeartbeatMonitor` uses the Streams Processor
API: it updates the stores on each heartbeat, and a scheduled **punctuator** scans every few seconds
for devices that have gone silent.

## Objectives

1. Run the monitor and watch it build up per-device state.
2. Crash and restart it, and watch it **restore that state from the changelog** — the flaw is gone.
3. Understand how Streams state stores and changelog topics give you this for free.

## Prerequisites

The Kafka stack running (single-broker for the online course, or the classroom cluster), plus the
Docker Maven/Java toolchain (`maven:3-jdk-11`, `openjdk:11`). You also need the **`device-simulator`
already built** from the [implementation lab](../04-Implement-Topics-And-Partitions/topics-and-partitions.md)
— it's what produces the heartbeats.

Create the topics if they don't exist:

```bash
docker exec broker kafka-topics --bootstrap-server :9092 --create --if-not-exists --topic device-heartbeat --partitions 1 --replication-factor 1
docker exec broker kafka-topics --bootstrap-server :9092 --create --if-not-exists --topic device-event --partitions 1 --replication-factor 1
```

## Step 1 — Build the Streams monitor

From this lab's `heartbeat-streams/` directory:

```bash
cd heartbeat-streams
docker run -it --rm -v "$PWD":/work -w /work \
  -v "$HOME/.m2/repository":/root/.m2/repository maven:3-jdk-11 mvn clean package
```

This produces `target/heartbeat-streams-1.0.0-SNAPSHOT.jar`.

## Step 2 — Start the device simulator

In a **second terminal**, start the simulator (from the implementation lab) so heartbeats start
flowing onto `device-heartbeat`:

```bash
cd ../04-Implement-Topics-And-Partitions/device-simulator
docker run --network docker_kafka_network --rm -it -v "$PWD:/pwd" -w /pwd openjdk:11 \
  java -jar target/device-simulator-app-1.0.0-SNAPSHOT.jar
```

Leave it running.

## Step 3 — Run the monitor and watch it build state

Back in the `heartbeat-streams` terminal:

```bash
docker run --network docker_kafka_network --rm -it -v "$PWD:/pwd" -w /pwd openjdk:11 \
  java -jar target/heartbeat-streams-1.0.0-SNAPSHOT.jar
```

On this first run there's nothing to restore, then the device count climbs as heartbeats arrive:

```
Starting heartbeat Streams monitor (offline after 20000 ms, checking every 5000 ms)...
[state] CREATED -> REBALANCING
>> Restored last-seen state for 0 device(s) from the changelog topic.
[state] REBALANCING -> RUNNING
Checking devices... currently tracking 1 device(s).
Checking devices... currently tracking 6 device(s).
Checking devices... currently tracking 6 device(s).
```

It's now tracking the live devices in its state store. (If you stop the *simulator*, after ~20s
you'll see `Device OFFLINE: …` lines, and an event is produced to `device-event`.)

## Step 4 — Crash it, restart it, watch the state survive

Stop the monitor with **`Ctrl-C`** (this is the "crash"). The throwaway container is removed, so its
local state directory is wiped — there is **no local copy of the state left**.

Now run the *exact same command again* (give it a few seconds after the stop so the previous instance
fully leaves the consumer group):

```bash
docker run --network docker_kafka_network --rm -it -v "$PWD:/pwd" -w /pwd openjdk:11 \
  java -jar target/heartbeat-streams-1.0.0-SNAPSHOT.jar
```

This time it **restores the state from the changelog** before it resumes:

```
[state] CREATED -> REBALANCING
>> Restored last-seen state for 7 device(s) from the changelog topic.
[state] REBALANCING -> RUNNING
Checking devices... currently tracking 7 device(s).
```

**`Restored last-seen state for 7 device(s)`** — even though the container's disk was wiped, the
monitor came back knowing every device it was watching and when it last heard from each. Contrast
that with the plain-Java `device-monitor`, which would print nothing restored and start from a blank
slate. The flaw is gone, and you didn't write a single line of persistence code.

## Step 5 (optional) — See where the state actually lives

Streams created a compacted changelog topic to hold the store. List it and peek inside:

```bash
docker exec broker kafka-topics --bootstrap-server :9092 --list | grep changelog
# heartbeat-streams-monitor-last-seen-store-changelog

docker exec broker kafka-console-consumer --bootstrap-server :9092 \
  --topic heartbeat-streams-monitor-last-seen-store-changelog --from-beginning \
  --property print.key=true \
  --value-deserializer org.apache.kafka.common.serialization.LongDeserializer --timeout-ms 5000
```

Each record is `device → last-seen-epoch-millis`. Because the topic is **compacted**, it keeps only
the latest value per device — it *is* the state store, materialised in Kafka. That's the same
"topic as a table" idea as the [compaction lab](../classroom/C4-Log-Compaction/log-compaction.md),
working under the hood for you.

## Discussion

- **This is *the* reason Kafka Streams exists.** Stateful stream processing (counts, aggregations,
  windows, joins, "last seen") is everywhere, and doing it correctly across crashes is hard. Streams
  gives you local, fast state (RocksDB on disk) *plus* a changelog in Kafka for durability and
  automatic restore — so your processing is fault-tolerant by default.
- **You saw three pieces fit together:** the [Streams word-count lab](wordcount-kafka-streaming.md)
  (a topology with a state store), the [compaction lab](../classroom/C4-Log-Compaction/log-compaction.md)
  (a compacted topic as a table), and this lab (the changelog *is* a compacted table that backs the
  store).
- **The plain-Java monitor wasn't "wrong" — it was missing the hard part.** You *could* add
  persistence by hand (write to a DB, reload on startup, handle partial writes…). Streams is that
  hard part, done for you and integrated with partitioning and rebalancing.
- **Restore time scales with state size.** Here it's a handful of devices and instant; a store with
  millions of keys takes longer to restore (Streams keeps a local RocksDB copy to avoid re-restoring
  every time, and `standby.replicas` can keep warm copies for faster failover).

## Cleanup

Stop the monitor and the simulator (`Ctrl-C`). The topics and the changelog can stay; to fully reset
the app's state:

```bash
docker exec broker kafka-consumer-groups --bootstrap-server :9092 --delete --group heartbeat-streams-monitor 2>/dev/null
for t in $(docker exec broker kafka-topics --bootstrap-server :9092 --list | grep heartbeat-streams-monitor); do \
  docker exec broker kafka-topics --bootstrap-server :9092 --delete --topic "$t"; done
```
