# `device-monitor` — build & run

Provided Java for the [topics-and-partitions](../topics-and-partitions.md) lab — the plain-Java heartbeat monitor (in-memory state — lost on restart).
You don't have to modify it, but if you want to (e.g. to show students a change), **edit the source
under `src/main/java/` and then build & run from *this* directory**:

## Build

Rebuilds the jar after any edit. (Mounts the repo root via `git` so the parent pom resolves — works
the same for every Java lab, whatever its depth.)

```bash
docker run --rm -v "$(git rev-parse --show-toplevel)":/work -w "/work/$(git rev-parse --show-prefix)" \
  -v "$HOME/.m2/repository":/root/.m2/repository maven:3-jdk-11 mvn clean package
```

## Run

Needs the Kafka stack up (`docker compose … up -d`). `Ctrl-C` to stop a long-running app.

```bash
docker run --network docker_kafka_network --rm -it -v "$PWD:/pwd" -w /pwd openjdk:11 \
  java -jar target/device-monitor-1.0.0-SNAPSHOT.jar
```

