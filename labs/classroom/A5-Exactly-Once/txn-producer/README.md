# `txn-producer` — build & run

Provided Java for the [exactly-once](../exactly-once.md) lab — the transactional producer (commit/abort + read_committed demo).
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
  java -Daction=commit -Dprefix=ok -Dcount=5 -jar target/txn-producer-1.0.0-SNAPSHOT.jar
```


The `-D…` flags are the demo defaults — change them on the command line (see the lab for the variations).
