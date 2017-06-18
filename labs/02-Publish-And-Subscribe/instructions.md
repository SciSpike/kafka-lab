# Publish and Subscribe

## Introduction

In this lab we'll use Java and Maven to create a typical Java based producer and consumer of Kafka messages.
You have two options for running Maven and Java.
\Either, you install them natively or you can run the tools through a docker container.

### Native install

If you already have Java and Maven installed, make sure you have:

* Java 8 (you can get away with Java 7 until we get to streaming)
* Maven 3.X

Simple Google searches should help you install both tools.

With a successful installation, you should be able to run `mvn -v` from command line.
E.g., here is what that may look like (it may look slightly different on your machine based on the maven version and the operating system you use):

```bash
$ mvn -v
Apache Maven 3.3.9 (bb52d8502b132ec0a5a3f4c09453c07478323dc5; 2015-11-10T10:41:47-06:00)
Maven home: /home/pgraff/.jenv/candidates/maven/current
Java version: 1.8.0_131, vendor: Oracle Corporation
Java home: /usr/lib/jvm/java-8-oracle/jre
Default locale: en_US, platform encoding: UTF-8
OS name: "linux", version: "4.4.0-78-generic", arch: "amd64", family: "unix"
```

### Docker install of Maven and Java

If you decide to use docker, you would want to open a docker instance with all the tools in the root of your lab and keep it open during the lab.

For OSX and Linux, you can simply run:

```bash
$ cd DIR_WHERE_MY_LAB_IS
$ docker run -it --rm --name lesson -v "$PWD":/usr/src/lesson -w /usr/src/lesson maven:3-jdk-8 bash
root@58b8ca1d738c:/usr/src/lesson#
```

If you are on Windows, you may have to replace the `$PWD` with the full path of your lab directory.

You are now running a `bash` shell inside your docker instance.
Your directory is mapped to `/usr/src/lesson` inside the docker instance.

You should be able to run the maven command to check that everything is working `mvn --version`.
The output should be:

```bash
root@58b8ca1d738c:/usr/src/lesson30# mvn --version
Apache Maven 3.5.0 (ff8f5e7444045639af65f6095c62210b5713f426; 2017-04-03T19:39:06Z)
Maven home: /usr/share/maven
Java version: 1.8.0_131, vendor: Oracle Corporation
Java home: /usr/lib/jvm/java-8-openjdk-amd64/jre
Default locale: en, platform encoding: UTF-8
OS name: "linux", version: "4.4.0-79-generic", arch: "amd64", family: "unix"
root@58b8ca1d738c:/usr/src/lesson#
```

For simplicity of the lab instruction, we'll simply refer to the maven builds using the native install. However, you should be able to use the docker image above to build any of the labs.
Hence, if you select to use the docker approach, when we specify

```
$ mvn package
```

We assume that you are inside your docker container when you do.

## Two separate labs

This lab consists of two parts.
In the first part, we'll create a producer that can create messages in Kafka.
In the second part, we'll consume these messages.

Both the `consumer` and `producer` is written in Java.

If you're not sure-footed in Java, you may want to simply study the solution or if you can pair program with someone that knows Java.

Here are the links to the labs:

1. [Producer lab](producer.md)
1. [Consumer lab](consumer.md)
