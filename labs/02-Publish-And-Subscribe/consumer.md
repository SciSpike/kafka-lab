# Developing Kafka Applications - Consumer API

In this lab, you will create a Kafka Consumer using the Java API. This is the continuation of the previous lab in which
a `KafkaProducer` was created to send messages to two topics: `user-events` and `global-events`.

## Objectives

1. Understand what the consumer Java code is doing
2. Compile and run the consumer program
2. Observe the interaction between producer and consumer programs

## Prerequisites

Like the previous lab, [Docker](https://www.docker.com) will be used to start a Kafka and Zookeeper server. We will also
use a [Maven Docker image](https://hub.docker.com/_/maven) to compile & package the Java code and
an [OpenJDK image](https://hub.docker.com/_/openjdk) to run it. You should have already completed the
previous    `KafkaProducer` lab so that there are messages ready in the Kafka server for the Consumer to process.

## Instructions

1. Open `consumer/src/main/java/com/example/Consumer.java` in your favorite text editor. Like the producer we saw in the
   previous lab, this is a fairly simple Java class but can be expanded upon in a real application. For example, after
   processing the incoming records from Kafka, you would probably want to do something interesting like store them
   in [HBase](https://hbase.apache.org/) for later analysis. This application has two main responsibilities:

    * Initialize and configure
      a [`KafkaConsumer`](http://kafka.apache.org/0100/javadoc/org/apache/kafka/clients/consumer/KafkaConsumer.html)
    * Poll for new records in an infinite loop

   The first thing to notice is that a `KafkaConsumer` requires a set of properties upon creation just like
   a `KafkaProducer`. You can add these properties directly to code but a better solution is to externalize them in a
   properties file.

2. Open `resources/consumer.properties` and you see that the required configuration is minimal like the producer.

    * `bootstrap.servers` is our required list of host/port pairs to connect to Kafka. In this case, we only have one
      server.
    * `key.deserializer` is the deserializer class for key that implements the `Deserializer` interface.
    * `value.deserializer` is the deserializer class for value that implements the `Deserializer` interface.
    * `group.id` is a string that uniquely identifies the group of consumer processes to which this consumer belongs. In
      our case, we are just using the value of `test` for an example.
    * `auto.offset.reset` determines what to do when there is no initial offset in Zookeeper or Kafka from which to read
      records. The first time that a consumer is run will be the first time that the Kafka broker has seen the consumer
      group that the consumer is using. The default behavior is to position newly created consumer groups at the end of
      existing data which means that the producer data that we ran previously would not be read. By setting this
      to `earliest`, we are telling the consumer to reset the offset to the smallest offset.
    * `boostrap.servers.docker` is only used if we detect that the code is running inside a Docker container.

Like the producer, there
are [many configuration options available for Kafka consumer](http://kafka.apache.org/documentation.html#consumerconfigs)
that should be explored for a production environment.

3. Open `consumer/src/main/java/com/example/Consumer.java` again. A consumer can subscribe to one ore more topics. In
   this lab, you can see that the consumer will listen to messages from two topics.

4. Once the consumer has subscribed to the topics, the consumer then polls for new messages in an infinite loop.

   For each iteration of the loop, the consumer will fetch records for the topics. On each poll, the consumer will use
   the last consumed offset as the starting offset and fetch sequentially. The `poll` method takes a timeout in
   milliseconds to spend waiting if data is not available in the buffer.

   The returned object of the `poll` method is a `ConsumerRecords` that implements `Iterable` containing all the new
   records. From there our example lab just uses a `switch` statement to process each type of topic. In a real
   application, you would do something more interesting here than output the results to `stdout`.

5. Now we are ready to compile and run the lab. In a terminal, change to the `lab` directory and run the following
   command:

   ```shell
   $ docker run -it --rm -v "$(cd "$PWD/../.."; pwd)":/course-root -w "/course-root/$(basename $(cd "$PWD/.."; pwd))/$(basename "$PWD")" -v "$HOME/.m2/repository":/root/.m2/repository maven:3-jdk-11 ./mvnw clean package
   ```

  On a windows machine, you have to replace the `$PWD` with the current directory and the `$HOME` with a directory where you have the `.m2` folder.


6. With the consumer now built, run it with the following command:

     ```
     $ docker run --network 02-publish-and-subscribe_default --rm -it -v "$PWD:/pwd" -w /pwd openjdk:11 java -jar target/pubsub-consumer-*.jar
     SLF4J: Failed to load class "org.slf4j.impl.StaticLoggerBinder".
     SLF4J: Defaulting to no-operation (NOP) logger implementation
     SLF4J: See http://www.slf4j.org/codes.html#StaticLoggerBinder for further details.
     ...
     ```

   After the consumer has processed all of the messages, start the producer again in another terminal window and you
   will see the consumer output the messages almost immediately. The consumer will run indefinitely until you
   press `ctrl-c` in the terminal window.

7. [OPTIONAL] Play with the performance.
   Before we shut down Kafka, you may want to spend some time playing with the perfomance of the programs. 
   We have created an optional lab for this which you can run here before going to step 8 and shutting down Kafka.
   Here is a link to the lab.

8. Finally, change back into the `docker/` directory in order to shut down the Kafka and Zookeeper servers.

    ```
    $ docker-compose down
    ```

### Conclusion

We have now seen in action a basic producer that sends messages to the Kafka broker and then a consumer to process them.
The examples we've shown here can be incorporated into a larger, more sophisticated application.

Congratulations, this lab is complete!
