# Kafka Course

## Welcome

First, welcome to this course on Kafka.

Although Kafka is quite simple to install, we decided to make base it on `Docker` and `Docker-Compose`.
This gives us a couple of advantages:

- Easier installation:
  - As long as you can get Docker to run, we know that the Kafka installation will work
  - Install docker, then simply run the `docker compose` file
- Consistency between Windows, Mac and Linux
- The ability to scale up and down the Kafka cluster

## Installing Docker

Our labs require that you have Docker installed and get Kafka up and running.

We describe how [here](docker/starting-docker.md).

## Link to the labs

[This link will lead you to all the labs and examples](labs/labs.md)

## Outline

*DAY 1*

* Introduction (Lecture ~ 20 min)
  * Who are we?
  * What is Kafka?
  * First lab
* Verify that everything is installed and working (Lab ~ 20 min)
  * Install Kafka through Docker
  * Run a simple example of Kafka
* Introduction to Kafka (Lecture ~ 30 min)
  * Kafka under the hood
  * What is a topic?
  * What is a partition?
  * What is a producer?
  * What is a consumer?
* Creating a topic and passing a message (Lab ~30 min)
  * Create a topic
  * Run a simple consumer
  * Run a simple producer
* Dissecting the first example (Lecture/Discussion ~ 30 min)
  * Walk-through of the first lab
  * Question and answers
* Design of Kafka topics and partitions (Lecture ~ 30 min)
  * Case study
  * How to select topics?
  * How to select partitions?
* Exercise: Designing topics and partitions (Group Project ~ 20 min)
  * Design topics and partitions

*DAY 2*

* Evaluation of the designs and suggested solutions (Discussion ~20 min)
  * Discussion of the suggested solution(s)
  * Recommended design of case study
* Implement Topics and Partitions for case study (Lab ~30 min)
  * Define a topic and partition in Kafka
  * Create a consumer and producer
  * Run a test script
* Scaling Kafka (Lecture ~30 min)
  * Kafka Brokers
  * Kafka Clusters
  * Cluster mirroring
  * Consumer groups
* Streaming APIs for Kafka (Lecture ~20 min)
  * What is streaming?
  * Why use streams?
  * Programming to streams
  * Example streams using Spark
* Streaming and IoT Case Study (Lab ~30 min)
  * Consume a stream from Kafka
  * Build a Spark application over the Kafka stream
* Kafka Administration and Integration (Lecture ~30 Min)
  * Integration with Big Data tools (Storm, Spark, Hadoop)
  * Kafka Connect
  * Certified Kafka connectors
  * Kafka administration
  * Kafka monitoring
  * Security
* Exactly once delivery
