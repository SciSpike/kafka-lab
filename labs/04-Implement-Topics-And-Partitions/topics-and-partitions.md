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

```
$ docker-compose up
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

To see the messages, let's run our usual console consumer. In a new terminal `cd` into the `docker` directory and then
run the Kafka consumer.

```
$ cd docker
$ docker-compose exec kafka kafka-console-consumer.sh --bootstrap-server kafka:9092 --topic device-heartbeat
```

After a few seconds you should start to see heartbeat messages being produced:

```
...
Scale 1 sent heartbeat at Sat Jun 17 15:43:06 CDT 2017
Scale 2 sent heartbeat at Sat Jun 17 15:43:10 CDT 2017
Heart monitor 2 sent heartbeat at Sat Jun 17 15:43:11 CDT 2017
Heart monitor 5 sent heartbeat at Sat Jun 17 15:43:13 CDT 2017
Scale 5 sent heartbeat at Sat Jun 17 15:43:13 CDT 2017
Scale 6 sent heartbeat at Sat Jun 17 15:43:19 CDT 2017
Scale 1 sent heartbeat at Sat Jun 17 15:43:21 CDT 2017
Heart monitor 6 sent heartbeat at Sat Jun 17 15:43:22 CDT 2017
Heart monitor 1 sent heartbeat at Sat Jun 17 15:43:24 CDT 2017
Scale 4 sent heartbeat at Sat Jun 17 15:43:24 CDT 2017
Heart monitor 2 sent heartbeat at Sat Jun 17 15:43:26 CDT 2017
Heart monitor 7 sent heartbeat at Sat Jun 17 15:43:27 CDT 2017
Heart monitor 5 sent heartbeat at Sat Jun 17 15:43:28 CDT 2017
Scale 5 sent heartbeat at Sat Jun 17 15:43:28 CDT 2017
Scale 6 sent heartbeat at Sat Jun 17 15:43:34 CDT 2017
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
Received heartbeat from: Scale 6 value: 2022-02-07T15:47:53.049305Z
Received heartbeat from: Scale 4 value: 2022-02-07T15:47:56.444218Z
Received heartbeat from: Heart monitor 7 value: 2022-02-07T15:47:58.672334Z
Received heartbeat from: Heart monitor 4 value: 2022-02-07T15:48:00.970748Z
Received heartbeat from: Scale 7 value: 2022-02-07T15:48:01.37685Z
Received heartbeat from: Heart monitor 3 value: 2022-02-07T15:48:01.400834Z
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
Scale 7 offline since Sat Jun 17 16:58:10 CDT 2017
Scale 7 is back online
Heart monitor 2 offline since Sat Jun 17 16:59:41 CDT 2017
Heart monitor 2 is back online
...
```

Congratulations. You finished the lab!
