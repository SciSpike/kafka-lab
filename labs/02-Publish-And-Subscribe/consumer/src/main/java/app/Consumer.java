package app;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

public class Consumer {
  private static boolean inDocker = new File("/.dockerenv").exists();

  public static void main(String[] args) throws IOException {
    KafkaConsumer<String, String> consumer;
    try (var stream = Consumer.class.getClassLoader().getResourceAsStream("consumer.properties")) {
      Properties props = new Properties();
      props.load(stream);
      props.setProperty("client.id", "02-consumer-" + UUID.randomUUID());
      props.setProperty("group.instance.id", props.getProperty("client.id"));

      if (inDocker) {
        props.setProperty("bootstrap.servers", props.getProperty("bootstrap.servers.docker"));
      }

      consumer = new KafkaConsumer<>(props);
    }

    try {
      consumer.subscribe(List.of("user-events", "global-events"));

      Duration timeout = Duration.ofMillis(100);

      while (true) {
        ConsumerRecords<String, String> records = consumer.poll(timeout);

        for (ConsumerRecord<String, String> record : records) {
          switch (record.topic()) {
            case "user-events":
              System.out.println(
                  "Received user-events message - key: "
                      + record.key()
                      + " value: "
                      + record.value());
              break;
            case "global-events":
              System.out.println("Received global-events message - value: " + record.value());
              break;
            default:
              throw new IllegalStateException(
                  "Shouldn't be possible to get message on topic " + record.topic());
          }
        }
      }
    } catch (Throwable throwable) {
      throwable.printStackTrace(System.err);
    }
  }
}
