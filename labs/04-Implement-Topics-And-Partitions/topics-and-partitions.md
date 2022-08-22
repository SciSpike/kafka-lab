# Topics and partitions

## Introduction

To support the online format, we have dramatically simplified this exercise.

We have left an exercise description for a more ambitious exercise in this directory if you want a harder challenge
after this course is finished.

We'll simply implement a heartbeat example.

Imagine that the devices used by the remote patients sends a heartbeat at least every minute to say they are alive and
well.

We want to implement a processor that listens to these heartbeats and when we have not heard from the device for one
minute, we want to sound an alarm.

## Start docker containers

Make sure you shut down the docker images from the last exercises, then change into this lab's directory and start
docker:

```bash
$ docker-compose up
```

## Create the topics

In a new terminal, change directory to where we have our docker-compose file (`04-Implement-Topics-And-Partitions`) and run the following commands:

```bash
$ docker-compose exec kafka kafka-topics.sh --bootstrap-server :9092 --create --replication-factor 1 --partitions 1 --topic device-event
$ docker-compose exec kafka kafka-topics.sh --bootstrap-server :9092 --create --replication-factor 1 --partitions 1 --topic device-heartbeat
```

## Build and run the device simulator

We've already created a device simulator app, but we have to build it first.

In a terminal, change to the lab's `device-simulator` directory and run the following command:

```shell
$ docker run -it --rm -v "$(cd "$PWD/../.."; pwd)":/course-root -w "/course-root/$(basename $(cd "$PWD/.."; pwd))/$(basename "$PWD")" -v "$HOME/.m2/repository":/root/.m2/repository maven:3-jdk-11 ./mvnw clean package
```

On a windows machine, you have to replace the `$PWD` with the current directory and the `$HOME` with a directory where you have the `.m2` folder.

## Start the device-simulator

Next, let's start the simulator of device messages.

```shell
$ docker run --network "$(cd .. && basename "$(pwd)" | tr '[:upper:]' '[:lower:]')_default" --rm -it -v "$PWD:/pwd" -w /pwd openjdk:11 java -jar target/device-simulator-app-*.jar
SLF4J: Failed to load class "org.slf4j.impl.StaticLoggerBinder".
SLF4J: Defaulting to no-operation (NOP) logger implementation
SLF4J: See http://www.slf4j.org/codes.html#StaticLoggerBinder for further details.
...
```

On a windows machine, you have to replace the `$PWD` with the current directory and the `$HOME` with a directory where you have the `.m2` folder.

We are now producing simulated device events.

## Create a simple console consumer

To see the messages, let's run our usual console consumer. In a new terminal `cd` into the `04-Implement-Topics-And-Partitions` directory and then
run the Kafka consumer.

```
$ cd docker
$ docker-compose exec kafka kafka-console-consumer.sh --bootstrap-server kafka:9092 --topic device-heartbeat
```

After a few seconds you should start to see heartbeat messages being produced:

```
...
2022-08-21T19:20:16.210887Z
2022-08-21T19:20:16.638141Z
2022-08-21T19:20:20.24739Z
2022-08-21T19:20:20.729122Z
2022-08-21T19:20:23.489956Z
2022-08-21T19:20:24.938794Z
2022-08-21T19:20:31.207251Z
2022-08-21T19:20:33.075107Z
2022-08-21T19:20:33.705245Z
2022-08-21T19:20:35.372573Z
2022-08-21T19:20:35.882868Z
2022-08-21T19:20:38.202446Z
2022-08-21T19:20:40.126325Z
2022-08-21T19:20:46.639412Z
2022-08-21T19:20:50.373135Z
2022-08-21T19:20:51.07784Z
2022-08-21T19:20:53.202233Z
2022-08-21T19:20:53.491849Z
2022-08-21T19:21:01.209427Z
2022-08-21T19:21:01.639625Z
2022-08-21T19:21:03.074633Z
2022-08-21T19:21:05.252201Z
2022-08-21T19:21:06.076764Z
2022-08-21T19:21:08.203343Z
2022-08-21T19:21:08.490369Z
...
```

## Create the device monitor

We've also written a monitor for you. You can find this monitor in the directory `device-monitor/`. Take some time to
study the code and see how it works.

First, build the application:

```shell
$ docker run -it --rm -v "$(cd "$PWD/../.."; pwd)":/course-root -w "/course-root/$(basename $(cd "$PWD/.."; pwd))/$(basename "$PWD")" -v "$HOME/.m2/repository":/root/.m2/repository maven:3-jdk-11 ./mvnw clean package
```

On a windows machine, you have to replace the `$PWD` with the current directory and the `$HOME` with a directory where you have the `.m2` folder.


Next, run it:

```shell
$ docker run --network "$(cd .. && basename "$(pwd)" | tr '[:upper:]' '[:lower:]')_default" --rm -it -v "$PWD:/pwd" -w /pwd openjdk:11 java -jar target/device-monitor-*.jar
```

On a windows machine, you have to replace the `$PWD` with the current directory and the `$HOME` with a directory where you have the `.m2` folder.


You should now see a set of output similar to this:

```
...
Checking devices...
Finished checking devices...
Received heartbeat from: Scale 3 value: 2022-08-21T19:21:24.940407Z
Received heartbeat from: Scale 1 value: 2022-08-21T19:21:31.638733Z
Received heartbeat from: Heart monitor 4 value: 2022-08-21T19:21:34.757311Z
Received heartbeat from: Scale 6 value: 2022-08-21T19:21:35.24983Z
Received heartbeat from: Heart monitor 2 value: 2022-08-21T19:21:35.373978Z
Received heartbeat from: Heart monitor 1 value: 2022-08-21T19:21:35.726856Z
Received heartbeat from: Heart monitor 7 value: 2022-08-21T19:21:36.079161Z
Received heartbeat from: Heart monitor 3 value: 2022-08-21T19:21:46.206831
ZChecking devices...
Finished checking devices...
...
```

### Create a console consumer online/offline messages

```
$ docker-compose exec kafka kafka-console-consumer.sh --bootstrap-server kafka:9092 --topic device-event
```

It may take some time before you see online or offline messages (see the device simulator and you'll see the randomness
of the heartbeat production).

You should eventually start to see devices go offline and online.

For example:

```
...
Scale 7 is back online
Heart monitor 2 offline since Sat Jun 17 16:59:41 CDT 2017
Heart monitor 2 is back online
...
```

Congratulations. You finished the lab!
