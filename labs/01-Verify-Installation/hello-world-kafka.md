# Hello World, Kafka

In this lab, you will install Kafka with Docker and verify it is working by creating a topic and sending some messages.

## Objectives

1. Install [Kafka](http://kafka.apache.org/) using [Docker](https://www.docker.com/products/overview)
2. Create a topic
3. Send some messages to the topic
4. Start a consumer and retrieve the messages

## Prerequisites

One of the easiest way to get started with Kafka is through the use of [Docker](https://www.docker.com). Docker allows
the deployment of applications inside software containers which are self-contained execution environments with their own
isolated CPU, memory, and network
resources. [Install Docker Desktop by following the directions appropriate for your operating system.](https://www.docker.com/get-started)
Make sure that you can run both the `docker` and `docker compose` command from the terminal.

## Instructions

1. Let's figure out the name of the container that runs Kafka.

```
docker ps
```

You should see something like this:

```
CONTAINER ID   IMAGE                                COMMAND                  CREATED          STATUS          PORTS                                                                                                                             NAMES
7b25e8945eb9   confluentinc/confluent-local:7.4.1   "/etc/confluent/dock…"   10 minutes ago   Up 10 minutes   0.0.0.0:8082->8082/tcp, :::8082->8082/tcp, 0.0.0.0:9092->9092/tcp, :::9092->9092/tcp, 0.0.0.0:9101->9101/tcp, :::9101->9101/tcp   broker
0fd43a1494d9   provectuslabs/kafka-ui:latest        "/bin/sh -c 'java --…"   10 minutes ago   Up 10 minutes   0.0.0.0:8080->8080/tcp, :::8080->8080/tcp                                                                                         docker-kafka-ui-1
```
Notice the id of the container and copy it. 
The CONTAINER ID of the Kafka container is `7b25e8945eb9` in the example above.

2. Create a topic called `helloworld` with a single partition and one replica.

We now have to run the command `docker exec -it <container_id> kafka-topics --bootstrap-server :9092 --create --replication-factor 1 --partitions 1 --topic helloworld`.

Using the ID from the example above, I would have to run the following command:

```bash
docker exec -it 7b25e8945eb9 kafka-topics --bootstrap-server :9092 --create --replication-factor 1 --partitions 1 --topic helloworld
```

Make sure you replace the `<container_id>` with the one you copied earlier.

3. You can now see the topic that was just created with the `--list` flag:

  ```
  docker exec -it <container_id> kafka-topics --bootstrap-server :9092 --list
  helloworld
  ```

5. Normally you would use the Kafka API from within your application to produce messages but Kafka comes with a command
   line _producer_ client that can be used for testing purposes. 
   Each line from standard input will be treated as aseparate message. 
   Type a few messages and leave the process running.

  ```
  docker exec -it <container_id> kafka-console-producer --bootstrap-server :9092 --topic helloworld
  Hello world!
  Welcome to Kafka.
  ```

> NOTE: use keystroke `ctrl-d` to end message production via the terminal.

6. Open another terminal window in the lesson directory. In this window, we can use Kafka's command line _consumer_ that
   will output the messages to standard out.

  ```
  docker exec -it <container_id> kafka-console-consumer --bootstrap-server :9092 --topic helloworld --from-beginning
  Hello world!
  Welcome to Kafka.
  ```

7. In the _producer_ client terminal, type a few more messages that you should now see echoed in the _consumer_
   terminal.

8. [OPTIONAL] You may want to try a bit more text to see how Kafka is able to keep up with a larger load of text. 
   For example, you may try to paste the complete work of "War and Peace".
   You can find the text here: https://www.gutenberg.org/files/2600/2600-0.txt.
   To do so, simply copy the complete text from a web browser and paste it into the kafka-producer.
   You may notice that the consumer is processing the text in batches as well as having no problem kepping up with the paste speed of your terminal.

8. Stop the producer and consumer terminals by issuing `ctrl-c`.

Congratulations, this lab is complete!
