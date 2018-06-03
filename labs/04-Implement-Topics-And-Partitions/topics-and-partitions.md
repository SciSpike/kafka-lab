# Topics and partitions

## Introduction

To support the online format, we have dramatically simplified this exercise.

We have left an exercise description for a more ambitious exercise in this directory if you want a harder challenge after this course is finished.

We'll simply implement a heartbeat example.

Imagine that the devices used by the remote patients sends a heartbeat at least every minute to say they are alive and well.

We want to implement a processor that listens to these heartbeats and when we have not heard from the device for one minute, we want to sound an alarm.

## Start docker containers

Make sure you shutdown the docker images from the last exercises. Enter the `docker` directory of this lab.

Edit the docker-compose file (or copy the one from the last lab) to enter your host id. A reminder: we got the IP address by running:

Mac:

```
$ ifconfig | grep inet
```

Windows:

```
$ ipconfig
```

Find the address in the output and update the `docker-compose.yml` file. If you need more explanations, we had more details in the Producer and Consumer labs we did earlier.

Next, start docker:

```
$ docker-compose up
```

## Creating the topics

Next we'll simply create the topics. Open a new terminal in the `docker` directory.

```
$ docker-compose exec kafka /opt/kafka/bin/kafka-topics.sh --create --zookeeper zookeeper:2181 --replication-factor 1 --partitions 1 --topic device-heartbeat
$ docker-compose exec kafka /opt/kafka/bin/kafka-topics.sh --create --zookeeper zookeeper:2181 --replication-factor 1 --partitions 1 --topic device-event
```

## Build and run the device simulator

We've already created a heartbeat simulator.

It needs to be built with maven as we saw in the previous exercise. With maven installed locally, run:

```
$ cd ../heartbeat-simulator
$ mvn package
```

If you don't have a locally installed maven, run the docker command in this directory:

```
$ cd heartbeat-simulator/
$ docker run -it --rm --name lesson -v "$PWD":/usr/src/lesson -w /usr/src/lesson maven:3-jdk-8 bash

root@0c00b230aa19:/usr/src/lesson30# mvn package
```

## Start the device-simulator

Next, let's start the simulator of heartbeats.

```
$ target/simulator
SLF4J: Failed to load class "org.slf4j.impl.StaticLoggerBinder".
SLF4J: Defaulting to no-operation (NOP) logger implementation
SLF4J: See http://www.slf4j.org/codes.html#StaticLoggerBinder for further details.
Press enter to quit
```

We are now producing simulated heartbeats.

## Create a simple console consumer

To see the messages, let's run our usual console consumer. In a new terminal `cd` into the `docker` directory and then run the Kafka consumer.

```
$ cd docker
$ docker-compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka:9092 --topic device-heartbeat
```

After a few seconds you should start to see heartbeat messages being produced. E.g.:

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

We've also written a monitor for you. You can find this monitor in the directory `device-monitor/`.

Take some time to study the code and see how it works.

To build and run this application, you have to run maven as before (here shown with native install):

```
$ cd device-monitor
$ mvn package
....
$ target/device-monitor
```

You should now see a set of output similar to this:

```
...
Received heartbeat from: Scale 5 value: Scale 5 sent heartbeat at Sat Jun 17 16:32:43 CDT 2017
Received heartbeat from: Scale 6 value: Scale 6 sent heartbeat at Sat Jun 17 16:32:49 CDT 2017
Received heartbeat from: Heart monitor 4 value: Heart monitor 4 sent heartbeat at Sat Jun 17 16:32:51 CDT 2017
Received heartbeat from: Scale 4 value: Scale 4 sent heartbeat at Sat Jun 17 16:32:54 CDT 2017
Received heartbeat from: Scale 2 value: Scale 2 sent heartbeat at Sat Jun 17 16:32:55 CDT 2017
Received heartbeat from: Scale 7 value: Scale 7 sent heartbeat at Sat Jun 17 16:32:55 CDT 2017
Received heartbeat from: Heart monitor 5 value: Heart monitor 5 sent heartbeat at Sat Jun 17 16:32:58 CDT 2017
Received heartbeat from: Scale 5 value: Scale 5 sent heartbeat at Sat Jun 17 16:32:58 CDT 2017
Checking devices...
Device has not been heard from for some time. Producing a new event: Scale 1
Device has not been heard from for some time. Producing a new event: Heart monitor 3
...
```

### Create a console consumer online/offline messages

In a new shell, go to the `docker` directory.

```
$ cd docker
$ docker-compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka:9092 --topic device-event
```

It may take some time before you see online or offline messages (see the device simulator and you'll see the randomness of the heartbeat production).

You should eventually start to see devices go offline and online.

E.g.:

```
...
Scale 7 offline since Sat Jun 17 16:58:10 CDT 2017
Scale 7 is back online
Heart monitor 2 offline since Sat Jun 17 16:59:41 CDT 2017
Heart monitor 2 is back online
...
```

## That's it.

Congratulations. You finished the lab!
