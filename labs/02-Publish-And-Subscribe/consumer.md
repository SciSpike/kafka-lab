# Developing Kafka Applications - Consumer API

In this lab, you will create a Kafka Consumer using the Java API. This is the continuation of the previous lab in which a Kafka Producer was created to send messages to two topics -- `user-events` and `global-events`.

## Objectives

1. Understand what the consumer Java code is doing
2. Compile and run the consumer program
2. Observe the interaction between producer and consumer programs

## Prerequisites

Like the first part of this of this lab, we will use a [Maven](https://maven.apache.org/) Docker image to compile the Kafka consumer Java application. You should have a text editor available with Java syntax highlighting for clarity. You will need a basic understanding of Java programming to follow the lab although coding will not be required. You should have already completed the previous Kafka Producer lab so that there are messages ready in the Kafka server for the Consumer to process.

## Instructions
1. Open `consumer/src/main/java/com/example/Consumer.java` in your favorite text editor. Like the `Producer` we saw in the previous lab, this is a fairly simple Java class but can be expanded upon in a real application. For example, after processing the incoming records from Kafka, you would probably want to do something interesting like store them in [HBase](https://hbase.apache.org/) for later analysis. This application has two main responsibilities:

    * Initialize and configure a [KafkaConsumer](http://kafka.apache.org/0100/javadoc/org/apache/kafka/clients/consumer/KafkaConsumer.html)
    * Poll for new records in an infinite loop

    The first thing to notice is that a `KafkaConsumer` requires a set of properties upon creation just like a `KafkaProducer`. You can add these properties directly to code but a better solution is to externalize them in a properties file. The following code instantiates a `KafkaConsumer` object using `resources/consumer.properties`.

    ```java
    KafkaConsumer<String, String> consumer;
    try (InputStream props = Resources.getResource("consumer.properties").openStream()) {
        Properties properties = new Properties();
        properties.load(props);
        consumer = new KafkaConsumer<>(properties);
    }
    ```

1. Open `resources/consumer.properties` and you see that the required configuration is minimal like the producer.

    ```
    bootstrap.servers=localhost:9092
    key.deserializer=org.apache.kafka.common.serialization.StringDeserializer
    value.deserializer=org.apache.kafka.common.serialization.StringDeserializer
    group.id=test
    auto.offset.reset=earliest
    ```

    * `bootstrap.servers` is our required list of host/port pairs to connect to Kafka. In this case, we only have one server.
    * `key.deserializer` is the deserializer class for key that implements the `Deserializer` interface.
    * `value.deserializer` is the deserializer class for value that implements the `Deserializer` interface.
    * `group.id` is a string that uniquely identifies the group of consumer processes to which this consumer belongs. In our case, we are just using the value of `test` for an example.
    * `auto.offset.reset` determines what to do when there is no initial offset in Zookeeper or Kafka from which to read records. The first time that a consumer is run will be the first time that the Kafka broker has seen the consumer group that the consumer is using. The default behavior is to position newly created consumer groups at the end of existing data which means that the producer data that we ran previously would not be read. By setting this to `earliest`, we are telling the consumer to reset the offset to the smallest offset.

    Like the producer, there are [many configuration options available for Kafka consumer](http://kafka.apache.org/documentation.html#consumerconfigs) that should be explored for a production environment.

1. Open `consumer/src/main/java/com/example/Consumer.java` again. A consumer can subscribe to one or more topics. In this lab, the consumer will listen to messages from two topics with the following code:

    ```java
    consumer.subscribe(Arrays.asList("user-events", "global-events"));
    ```

1. Once the consumer has subscribed to the topics, the consumer can poll for new messages in the following loop:

    ```java
    while (true) {
        ConsumerRecords<String, String> records = consumer.poll(100);

        for (ConsumerRecord<String, String> record : records) {
            switch (record.topic()) {
                case "user-events":
                    System.out.println("Received user-events message - key: " + record.key() + " value: " + record.value());
                    break;
                case "global-events":
                    System.out.println("Received global-events message - value: " + record.value());
                    break;
                default:
                    throw new IllegalStateException("Shouldn't be possible to get message on topic " + record.topic());
            }
        }
    }
    ```

    For each iteration of the loop, the consumer will fetch records for the topics. On each poll, the consumer will use the last consumed offset as the starting offset and fetch sequentially. The `poll` method takes a timeout in milliseconds to spend waiting if data is not available in the buffer.

    The returned object of the `poll` method is an `Iterable` that contains all the new records. From there our example lab just uses a `switch` statement to process each type of topic. In a real application, you would do something more interesting here than output the results to `stdout`.

1. Now we are ready to compile and run the lab. In a terminal, change to the `consumer` directory and run the following  [Maven](https://maven.apache.org/) targets:

    ```
    $ docker run -it --rm --name lesson -v "$PWD":/usr/src/lesson -w /usr/src/lesson maven:3-jdk-8 mvn clean package
    ```

1. For convenience, the project is set up so that the `package` target produces a single executable: `target/consumer`. Run the consumer:

    ```
    $ target/consumer
    SLF4J: Failed to load class "org.slf4j.impl.StaticLoggerBinder".
    SLF4J: Defaulting to no-operation (NOP) logger implementation
    SLF4J: See http://www.slf4j.org/codes.html#StaticLoggerBinder for further details.
    Received user-events message - key: user_id_0 value: some_value_148511272601285
    Received user-events message - key: user_id_1 value: some_value_148511557371815
    Received user-events message - key: user_id_2 value: some_value_148511557456741
    ....
    ```

    After the consumer has processed all of the messages, start the producer again in another terminal window and you will see the consumer output the messages almost immediately. The consumer will run indefinitely until you press `Ctrl-C` in the terminal window.

1. Finally, change back into the `docker/` directory in order to shut down the Kafka and Zookeeper servers.

    ```
    $ docker-compose down
    ```

### Conclusion
We have now seen in action a basic producer that sends messages to the Kafka broker and then a consumer to process them. The examples we've shown here can be incorporated into a larger, more sophisticated application.

Congratulations, this lab is complete!
