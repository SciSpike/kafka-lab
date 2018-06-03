# Hello World, Kafka

In this lab, you will install Kafka with Docker and verify it is working by creating a topic and sending some messages.

## Objectives

1. Install [Kafka](http://kafka.apache.org/) using [Docker](https://www.docker.com/products/overview)
2. Create a topic
3. Send some messages to the topic
4. Start a consumer and retrieve the messages

## Prerequisites

One of the easiest way to get started with Kafka is through the use of [Docker](https://www.docker.com). Docker allows the deployment of applications inside software containers which are self-contained execution environments with their own isolated CPU, memory, and network resources. [Install Docker by following the directions appropriate for your operating system.](https://www.docker.com/products/overview) Make sure that you can run both the `docker` and `docker-compose` command from the terminal.

## [OPTIONAL] Alias

Because we use docker and docker-compose, the commands to run the kafka CLI are absurdly long.

We have all of the commands listed in the exercise below, so you can simply copy and paste, but as you get more advanced, you may want to experiment with the CLI.

One way to make this simpler is to alias your commands. When we run the Kafka commands in the running docker image, we reach into the image and run a command in the directory `/opt/kafka_2.11-0.10.1.1/bin/`.

This means that all of our commands are preseeded with the following noise: `docker-compose exec kafka /opt/kafka_2.11-0.10.1.1/bin/`

You may want to alias these commands. In Linux and Mac, you can simply create aliases in your terminal setup.

E.g., say you run bash, you can open the `~/.bash_profile` file with your favorite editor and enter something like this:

```
  alias ktopics='docker-compose exec kafka /opt/kafka/bin/kafka-topics.sh'
  alias kconsole-producer='docker-compose exec kafka /opt/kafka/bin/kafka-console-producer.sh'
  alias kconsole-consumer='docker-compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh'
```

When you start new shells, you can now simply run:

```bash
  ktopics {OPTIONS} command
```

To update your current shell (so that you don't have to close your terminal and start a new one), run:

```bash
. ~/.bash_profile
```

Another alternative is to run a bash shell inside the Docker container.

E.g.:

```bash
$ docker-compose exec kafka /usr/bin/env bash

bash-4.3#
```

You are now running inside the container and all the commands should work (and autocomplete).

## Instructions

1. Open a terminal in this lab directory: `labs/01-Verify-Installation`.

2. Start the Kafka and Zookeeper processes using Docker Compose:

  ```
  $ docker-compose up
  ```

  The first time you run this command, it will take a while to download the appropriate Docker images.

3. Open an additional terminal window in the lesson directory, `lelabs/01-Verify-Installation`. We are going to create a topic called `helloworld` with a single partition and one replica:

  ```
  $ docker-compose exec kafka /opt/kafka/bin/kafka-topics.sh --create --zookeeper zookeeper:2181 --replication-factor 1 --partitions 1 --topic helloworld
  ```

4. You can now see the topic that was just created with the `--list` flag:

  ```
  $ docker-compose exec kafka /opt/kafka/bin/kafka-topics.sh --list --zookeeper zookeeper:2181
  helloworld
  ```

5. Normally you would use the Kafka API from within your application to produce messages but Kafka comes with a command line _producer_ client that can be used for testing purposes. Each line from standard input will be treated as a separate message. Type a few messages and leave the process running.

  ```
  $ docker-compose exec kafka /opt/kafka/bin/kafka-console-producer.sh --broker-list kafka:9092 --topic helloworld
  Hello world!
  Welcome to Kafka.
  ```

6. Open another terminal window in the lesson directory. In this window, we can use Kafka's command line _consumer_ that will output the messages to standard out.

  ```
  $ docker-compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka:9092 --topic helloworld --from-beginning
  Hello world!
  Welcome to Kafka.
  ```

7. In the _producer_ client terminal, type a few more messages that you should now see echoed in the _consumer_ terminal.

8. Stop the producer and consumer terminals by issuing a **Ctrl-C**.

9. Finally, stop the Kafka and Zookeeper servers with Docker Compose:

  ```
  $ docker-compose down
  ```

Congratulations, this lab is complete!
