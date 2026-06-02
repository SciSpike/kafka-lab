# GPS Pump — Producer Internals

This is a supplemental reference for the `gps-pump` producer used by the
[IoT Kafka Stream lab](../../iot-kafka-lab.md). The main lab walks through
building and running the app; this file explains how the producer code is
organized for anyone who wants to dig deeper.

## Files of interest

- `src/main/java/app/GpsDeviceSimulatorApp.java` — bootstrap. Loads
  `producer.properties`, creates the `KafkaProducer`, and starts a
  `GpsDeviceSimulator`.
- `src/main/java/app/GpsDeviceSimulator.java` — reads `data.tsv` from the
  classpath and streams each tab-delimited line to the `gps-locations` topic,
  looping back to the top of the file when it reaches the end.
- `src/main/resources/data.tsv` — recorded vehicle GPS samples. Each row is
  `deviceId  timestamp  speed  heading  longitude  latitude` (tab-separated).
- `src/main/resources/producer.properties` — externalized Kafka producer
  configuration.

## Configuration

`producer.properties` contains:

```properties
retries=0
bootstrap.servers=localhost:9092
bootstrap.servers.docker=broker:29092
key.serializer=org.apache.kafka.common.serialization.StringSerializer
value.serializer=org.apache.kafka.common.serialization.StringSerializer
```

- `bootstrap.servers` — used when the JVM runs on the host.
- `bootstrap.servers.docker` — used when the JVM runs inside a container
  attached to `docker_kafka_network`. The app detects this by checking for
  `/.dockerenv` and overrides `bootstrap.servers` automatically.
- `key.serializer` / `value.serializer` — both keys and values are strings.

## Message shape

Each message looks like:

- topic: `gps-locations`
- key:   none
- value: a raw tab-delimited line lifted directly out of `data.tsv`

The downstream [`gps-monitor`](../gps-monitor) splits this line, filters for
`speed < 1` ("parked"), geohashes the coordinates, and counts how often each
vehicle parks at each location.

## Build and run

The build and run commands live in the main
[IoT Kafka Stream lab](../../iot-kafka-lab.md). The short version, run from
this directory:

```shell
# build
docker run -it --rm \
  -v "$(cd "$PWD/../../../.."; pwd)":/course-root \
  -w /course-root/labs/06-Streaming/iot-kafka/gps-pump \
  -v "$HOME/.m2/repository":/root/.m2/repository \
  maven:3-jdk-11 ./mvnw clean package

# run
docker run --network docker_kafka_network --rm -it \
  -v "$PWD:/pwd" -w /pwd \
  openjdk:11 java -jar target/gps-pump-*.jar
```
