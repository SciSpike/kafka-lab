# Device Simulator — Producer Internals

This is a supplemental reference for the device-simulator producer used by the
[Topics and Partitions lab](../topics-and-partitions.md). The main lab walks
through building and running the app; this file explains how the producer code
is organized for anyone who wants to dig deeper.

## Files of interest

- `src/main/java/app/DeviceSimulatorApp.java` — bootstrap, loads properties,
  creates the `KafkaProducer`, and spins up a set of `DeviceSimulator`
  instances.
- `src/main/java/app/DeviceSimulator.java` — one timer per simulated device.
  On each tick it flips a coin and, if heads, sends a heartbeat to the
  `device-heartbeat` topic with the device id as the key.
- `src/main/resources/producer.properties` — externalized Kafka producer
  configuration plus the `simulator.count` knob that controls how many devices
  of each kind to simulate.

## Configuration

`producer.properties` contains:

```properties
simulator.count=7
bootstrap.servers=localhost:9092
bootstrap.servers.docker=broker:29092
key.serializer=org.apache.kafka.common.serialization.StringSerializer
value.serializer=org.apache.kafka.common.serialization.StringSerializer
```

- `simulator.count` — number of heart monitors *and* scales to simulate
  (`DeviceSimulatorApp` creates one of each per index, so a count of 7 means 14
  simulated devices).
- `bootstrap.servers` — used when the JVM runs on the host (the Kafka
  `PLAINTEXT_HOST` listener exposed via the Docker port mapping).
- `bootstrap.servers.docker` — used when the JVM runs inside a container
  attached to `docker_kafka_network`. The simulator detects this by checking
  for `/.dockerenv` and overrides `bootstrap.servers` automatically.
- `key.serializer` / `value.serializer` — both keys and values are strings.

## Message shape

Each heartbeat looks like:

- topic: `device-heartbeat`
- key:   the device id (e.g. `Heart monitor 3`)
- value: an ISO-8601 timestamp (e.g. `2026-06-02T10:14:11.248962Z`)

Because the device id is the key, all heartbeats from the same device will
land on the same partition — which is what the
[design discussion](../patient-monitoring-exercise-full.md) recommends so a
single device's stream stays ordered.

## Build and run

The build and run commands live in the main
[Topics and Partitions lab](../topics-and-partitions.md). The short version,
run from this directory:

```shell
# build
docker run -it --rm \
  -v "$(cd "$PWD/../.."; pwd)":/course-root \
  -w "/course-root/$(basename $(cd "$PWD/.."; pwd))/$(basename "$PWD")" \
  -v "$HOME/.m2/repository":/root/.m2/repository \
  maven:3-jdk-11 ./mvnw clean package

# run
docker run --network docker_kafka_network --rm -it \
  -v "$PWD:/pwd" -w /pwd \
  openjdk:11 java -jar target/device-simulator-app-*.jar
```

The `-it` is intentional — the app sits at a `Press enter to quit` prompt and
needs an attached stdin to stay alive.
