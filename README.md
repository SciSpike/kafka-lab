# Kafka Course

## Welcome

First, welcome to this course on Kafka.

Although Kafka is quite simple to install, we decided to make base it on `Docker` and `Docker-Compose`.
This gives us a couple of advantages:

- Easier installation:
  - As long as you can get Docker to run, we know that the Kafka installation will work
  - Install docker, then simply run the `docker compose` file
- Consistency between Windows, Mac and Linux
- The ability to scale up and down the Kafka cluster

## Installing Docker

Our labs require that you have Docker installed and get Kafka up and running.

We describe how [here](docker/starting-docker.md).

## Link to the labs

There are **two tracks**, each with its own Docker stack — start whichever one you're running:

- **Online (2×3h, self-paced)** — the standard follow-the-instructions labs, on the lean
  **single-broker** stack (`cd docker && docker compose up -d`):
  [`labs/labs.md`](labs/labs.md)
- **Classroom (2 full days, instructor-led)** — richer exercises that deliberately trigger
  real-world Kafka failures (lost writes, duplicate consumption) and measure the effect of
  tuning knobs, on a **3-broker cluster** started once and left running
  (`cd docker && docker compose -f docker-compose-classroom.yaml up -d`):
  [`labs/classroom/classroom-labs.md`](labs/classroom/classroom-labs.md)

## Outline — Online course (2 × 3 hours)

The self-paced online format: each lecture is paired with a short, follow-the-steps lab. Same slides
as the classroom course below — the classroom course just adds many more (and richer) exercises.

*DAY 1*

* Introduction (Lecture ~ 20 min)
  * Who are we?
  * What is Kafka?
  * First lab
* Verify that everything is installed and working (Lab ~ 20 min)
  * Install Kafka through Docker
  * Run a simple example of Kafka
* Introduction to Kafka (Lecture ~ 30 min)
  * Kafka under the hood
  * What is a topic?
  * What is a partition?
  * What is a producer?
  * What is a consumer?
* Creating a topic and passing a message (Lab ~30 min)
  * Create a topic
  * Run a simple consumer
  * Run a simple producer
* Dissecting the first example (Lecture/Discussion ~ 30 min)
  * Walk-through of the first lab
  * Question and answers
* Design of Kafka topics and partitions (Lecture ~ 30 min)
  * Case study
  * How to select topics?
  * How to select partitions?
* Exercise: Designing topics and partitions (Group Project ~ 20 min)
  * Design topics and partitions

*DAY 2*

* Evaluation of the designs and suggested solutions (Discussion ~20 min)
  * Discussion of the suggested solution(s)
  * Recommended design of case study
* Implement Topics and Partitions for case study (Lab ~30 min)
  * Define a topic and partition in Kafka
  * Create a consumer and producer
  * Run a test script
* Scaling Kafka (Lecture ~30 min)
  * Kafka Brokers
  * Kafka Clusters
  * Cluster mirroring
  * Consumer groups
* Streaming APIs for Kafka (Lecture ~20 min)
  * What is streaming?
  * Why use streams?
  * Programming to streams
  * Example streams using Spark
* Streaming and IoT Case Study (Lab ~30 min)
  * Consume a stream from Kafka
  * Build a Spark application over the Kafka stream
* Kafka Administration and Integration (Lecture ~30 Min)
  * Integration with Big Data tools (Storm, Spark, Hadoop)
  * Kafka Connect
  * Certified Kafka connectors
  * Kafka administration
  * Kafka monitoring
  * Security
* Exactly once delivery

## Outline — Classroom course (2 full days)

**Same slides and lecture flow as the online course above** — the only difference is the exercises.
The two online sessions use light "follow the steps" labs; over two full days there is time for the
richer, failure-focused exercises in [`labs/classroom/`](labs/classroom/classroom-labs.md), which
deliberately trigger real-world Kafka problems and measure the effect of tuning knobs. The lecture
headings below match the slide deck; under each one are the exercises that belong with it. (For
per-exercise staging, timing and talking points, see the separate instructor guide.)

*DAY 1*

* **Introduction** (Lecture)
  * Lab: [Verify the installation — Hello World, Kafka](labs/01-Verify-Installation/hello-world-kafka.md)
* **Introduction to Kafka** (Lecture) — under the hood; topics, partitions, producers, consumers
  * Lab: create a topic and pass a message (the console part of Hello World, above)
  * The reference implementation in Java: [Producer](labs/02-Publish-And-Subscribe/producer.md) → [Consumer](labs/02-Publish-And-Subscribe/consumer.md)
* **Dissecting the first example** (Lecture / Discussion)
* **Design of Kafka topics and partitions** (Lecture + group project)
  * Group project: [Designing topics and partitions](labs/03-Designing-Topics-And-Partitions/design.md)
  * [C3 — Ordering & keys](labs/classroom/C3-Ordering-And-Keys/ordering-and-keys.md) — proves the partition-key choice from the design
* **Implement topics and partitions** (Lab)
  * Lab: [Heartbeat monitor](labs/04-Implement-Topics-And-Partitions/topics-and-partitions.md)
  * Discussion: [find-the-flaw](labs/04-Implement-Topics-And-Partitions/find-the-flaw.md) — sets up the Exactly-once exercises on Day 2

*DAY 2*

* **Scaling Kafka** (Lecture) — brokers, clusters, mirroring, consumer groups
  * [C1 — Multi-broker cluster: replication, ISR, `acks=all`, failover](labs/classroom/C1-Multi-Broker-Cluster/multi-broker-cluster.md)
  * [A3 — Rebalancing: in-flight reprocessing when a consumer dies](labs/classroom/A3-Rebalance-Reprocessing/rebalance-reprocessing.md)
  * [A4 — The slow-poll eviction (`max.poll.interval.ms`)](labs/classroom/A4-Slow-Poll-Eviction/slow-poll-eviction.md) *(provided Java; build & run via Docker)*
  * [B2 — Partitions & consumer parallelism: where adding consumers stops helping](labs/classroom/B2-Partitions-And-Parallelism/partitions-and-parallelism.md)
  * [C2 — Consumer lag & backpressure](labs/classroom/C2-Consumer-Lag/consumer-lag.md)
* **Streaming APIs for Kafka** (Lecture) + IoT case study
  * Lead-in: [C4 — Log compaction & tombstones: a topic as a changelog of state](labs/classroom/C4-Log-Compaction/log-compaction.md)
  * Lab: [Word count with Kafka Streams](labs/06-Streaming/wordcount-kafka-streaming.md)
  * Lab: [IoT streaming case study](labs/06-Streaming/iot-kafka-lab.md)
* **Kafka Administration and Integration** (Lecture) — Connect, administration, monitoring, tooling
  * [B1 — Producer performance tuning](labs/classroom/B1-Producer-Tuning/producer-tuning.md)
  * [B3 — Consumer fetch tuning (`fetch.min.bytes`)](labs/classroom/B3-Consumer-Fetch-Tuning/consumer-fetch-tuning.md)
  * [C5 — Poison pill, dead-letter topics & offset reset](labs/classroom/C5-Poison-Pill-And-DLQ/poison-pill-and-dlq.md)
* **Exactly-once delivery** (Lecture)
  * [A1 — Crash before the ack: `acks` and lost writes](labs/classroom/A1-Crash-Before-Ack/crash-before-ack.md)
  * [A2 — Duplicate consumption & idempotent consumers](labs/classroom/A2-Duplicate-Consumption/duplicate-consumption.md)
