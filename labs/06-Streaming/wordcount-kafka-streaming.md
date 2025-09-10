# Word Count with Kafka Streaming

In this lab, you'll use Kafka Streaming to count words in a stream of lines.

## The Kafka streaming app

### Examine the code

Using your favorite editor, open the file `wordcount-kafka-streaming/src/main/java/app/StreamExample.java`.

First, we get our Kafka properties, then instantiate a `StreamBuilder` in order to get a reference to a `KStream` via
the builder's `stream` method, passing the topic name from which we'll consume messages. Then, we establish our stream's
topology.

* `flatMapValues` takes the incoming stream of lines and returns a stream of words after splitting them on word
  boundaries.
* `filter` keeps only those items in the stream that are not blank, in case any of those managed to slip into the word
  stream.
* `map` transforms each word into a `KeyValue` instance using the word as the key _and_ the value.
* `groupByKey` groups the `KeyValue` instances by word (the key).
* `count` creates a `KTable` containing the counts of each word.
* `toStream` converts the `KTable` to a `KStream` so that we can then produce values to the output topic.
* `map` converts each value in the stream to a `String` formatted with the word and its count separated by a colon.
* `to` streams the formatted word counts to the output topic.

> NOTE: this topology isn't actually executed until the stream is started and lines are presented to the stream.

Now that we've created our stream processing topology, we instantiate a `KafkaStreams` instance, giving it
our `Topology` and hook it up to Kafka via our Kafka client properties, then `start` the stream.

Lastly, for convenience, we wait for the user to hit enter to `close` the stream and exit.

### Build the streaming app

Now that we have coded our app, we need to build it. Fortunately, we can use `docker` for this so that we don't have to
have `maven` and its prerequisites installed locally. Open a terminal in the lab's `wordcount-kafka-streaming` directory and issue
the following command:

```shell
docker run -it --rm -v "$(cd "$PWD/../.."; pwd)":/course-root -w "/course-root/$(basename $(cd "$PWD/.."; pwd))/$(basename "$PWD")" -v "$HOME/.m2/repository":/root/.m2/repository maven:3-jdk-11 ./mvnw clean package
```

On a windows machine, you have to replace the `$PWD` with the current directory and the `$HOME` with a directory where you have the `.m2` folder.

The command above will build and package our uber jar with the application and all of its dependencies.

## Run Kafka

If you are running this lab for the first time, you need to run Kafka.

See the [Kafka lab](../../docker/start-kafka.md) for instructions on how to run Kafka.

## Start our streaming application

Before we start our Kafka streaming application we have to set up some topics and publish/subscribe to the topics.

In yet another terminal, change into the lab's root directory again and this time, start a bash session in the _kafka_
container (replace `<container_id>` with the container id):

```shell
docker exec -it <container_id> bash
```

At the subsequent prompt, create the topics & start a Kafka console consumer:

```shell
[appuser@broker ~]$ for it in input output; do kafka-topics --create --bootstrap-server :9092 --topic stream-$it; done
[appuser@broker ~]$ kafka-console-consumer --bootstrap-server :9092 --topic stream-output --from-beginning --property print.key=true
```

In another terminal, start a bash session and pump some lines into the kafka console producer:

```shell
docker exec -it <container_id> bash
[appuser@broker ~]$ cat /data/ickle-pickle-tickle.txt | kafka-console-producer --bootstrap-server :9092 --topic stream-input
```

Now, let's start our streaming application connecting to Kafka running in our Docker environment:

```shell
docker run --network docker_kafka_network --rm -it -v "$PWD:/pwd" -w /pwd openjdk:11 java -jar target/wordcount-kafka-solution-*.jar
```

On a windows machine, you have to replace the `$PWD` with the current directory and the `$HOME` with a directory where you have the `.m2` folder.


This should produce the output count in the terminal where we're running the console consumer:

```shell
went	1
for	1
ride	1
a	2
...
ickle	8
pickle	8
tickle	8
me	20
too	7
```

For fun, you can submit the full text of Leo Tolstoy's "War & Peace"!

```shell
[appuser@broker ~]$ cat /data/war-and-peace.txt | kafka-console-producer --bootstrap-server :9092 --topic stream-input
```

Congratulations, you've completed this lab!
