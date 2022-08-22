# Developing Kafka Applications - Producer API

In this lab, you will create a Kafka Producer using the Java API. The next lab will be the creation of the Kafka
Consumer so that you can see an end to end example using the API.

## Objectives

1. Create topics on the Kafka server for the producer to send messages
2. Understand what the producer Java code is doing
3. Build & package the producer
4. Run the producer

## Prerequisites

Like the previous lab, [Docker](https://www.docker.com) will be used to start a Kafka and Zookeeper server. We will also
use a [Maven Docker image](https://hub.docker.com/_/maven) to compile & package the Java code and
an [OpenJDK image](https://hub.docker.com/_/openjdk) to run it.

You should have a text editor available with Java syntax highlighting for clarity. You will need a basic understanding
of Java programming to follow the lab although coding will not be required. The Kafka Producer example will be explained
and then you will compile and execute it against the Kafka server.

## Instructions

All the directory references in this lab is relative to where you expended the lab files
and `labs/02-Publish-And-Subscribe`

1. Open a terminal in this lesson's root directory.

2. Start the Kafka and Zookeeper containers using Docker Compose:

    ```
    $ docker-compose up
    ```

3. Open an additional terminal window in the lesson directory.

4. Open `producer/src/main/java/app/Producer.java` in your text editor. This class is fairly simple Java application but
   contains all the functionality necessary to operate as a Kafka Producer. The application has two main
   responsibilities:

    * Initialize and configure
      a [KafkaProducer](http://kafka.apache.org/0100/javadoc/org/apache/kafka/clients/producer/KafkaProducer.html)
    * Send messages to topics with the `KafkaProducer` object

   To create our producer object, we must create an instance of `org.apache.kafka.clients.producer.KafkaProducer` which
   requires a set of properties for initialization. While it is possible to add the properties directly in Java code, a
   more likely scenario is that the configuration would be externalized in a properties file.

   Open `resources/producer.properties` and you can see that the configuration is minimal.

    * `acks` is the number of acknowledgments the producer requires the leader to have received before considering a
      request complete. This controls the durability of records that are sent. A setting of `all` is the strongest
      guarantee available.
    * Setting `retries` to a value greater than 0 will cause the client to resend any record whose send fails with a
      potentially transient error.
    * `bootstrap.servers` is our required list of host/port pairs to connect to Kafka. In this case, we only have one
      server. The Docker Compose file exposes the Kafka port so that it can be accessed through `localhost`.
    * `key.serializer` is the serializer class for key that implements the `Serializer` interface.
    * `value.serializer` is the serializer class for value that implements the `Serializer` interface.
    * `boostrap.servers.docker` is only used if we detect that the code is running inside a Docker container.

   There are
   [many configuration options available for Kafka producers](http://kafka.apache.org/documentation.html#producerconfigs)
   that should be explored for a production environment.

5. Once you have a `KafkaProducer` instance, you can post messages to a topic using
   the [`ProducerRecord`](http://kafka.apache.org/0100/javadoc/org/apache/kafka/clients/producer/ProducerRecord.html).
   A `ProducerRecord` is a key/value pair that consists of a topic name to which the record is being sent, an optional
   partition number, an optional key, and required value.

   In our lab, we are going to send a bunch of messages in a loop. Each iteration of the loop will consist of sending a
   message to the `user-events` topic with a key/value pair. Every so often, we also send a randomized message to
   the `global-events` topic. The message sent to `global-events` does not have a key specified which normally means
   that Kafka will assign a partition in a round-robin fashion but our topic only contains 1 partition.

   After the loop is complete, it is important to call `producer.close()` to end the producer lifecycle. This method
   blocks the process until all the messages are sent to the server. This is called in the `finally` block to guarantee
   that it is called. It is also possible to use a Kafka producer in
   a [try-with-resources statement](https://docs.oracle.com/javase/tutorial/essential/exceptions/tryResourceClose.html).

6. Now we are ready to compile the lab. In a terminal, change to the lab's `producer` directory and run the following
   command:

If you are on a Mac (or in linux), this should work:

   ```shell
   $ docker run -it --rm -v "$(cd "$PWD/../.."; pwd)":/course-root -w "/course-root/$(basename $(cd "$PWD/.."; pwd))/$(basename "$PWD")" -v "$HOME/.m2/repository":/root/.m2/repository maven:3-jdk-11 ./mvnw clean package
   ```

On a Windows machine, the `$PWD` and `pwd` commands may not work (unless you run in a Unix-compliant shell). PWD means `print current directory`. 

The trick we use above is to map the directory two steps down from the current (which is where the labs are installed on your machine). 
So say you had our labs installed in a directory called `/tmp/MyKafkaLabs`, you are now in the directory `/tmp/MyKafkaLabs/kafka-lab/labs/02-Publish-And-Subscribe`.
The script `"$(cd "$PWD/../.."; pwd)"` simply out`puts the path two steps down from here, which would be `/tmp/MyKafkaLabs/kafka-lab`. 

The next thing to look out for on a Windows machine is the use of `$HOME`. 
This simply means your home directory. You can always replace `$HOME` with your home directory.

7. With the producer now built, run it with the following command:

     ```
     $ docker run --network 02-publish-and-subscribe_default --rm -it -v "$PWD:/pwd" -w /pwd openjdk:11 java -jar target/pubsub-producer-*.jar
     SLF4J: Failed to load class "org.slf4j.impl.StaticLoggerBinder".
     SLF4J: Defaulting to no-operation (NOP) logger implementation
     SLF4J: See http://www.slf4j.org/codes.html#StaticLoggerBinder for further details.
     ...
     ```

8. *Don't* stop the Kafka and Zookeeper servers because they will be used in the next lab focusing on the Consumer API.

### Conclusion

We have now successfully sent a series of messages to Kafka using the Producer API. In the next lab, we will write a
consumer program to process the messages from Kafka.

Congratulations, this lab is complete!
