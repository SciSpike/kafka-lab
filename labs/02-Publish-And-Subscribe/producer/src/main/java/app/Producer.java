package app;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.io.File;
import java.io.IOException;
import java.util.Properties;
import java.util.UUID;

public class Producer {

  private static String[] globalEvents = {
    "maintenance_begin", "maintenance_end", "plan_removed", "plan_added", "sale_begin", "sale_end"
  };

  private static boolean inDocker = new File("/.dockerenv").exists();

  public static void main(String[] args) throws IOException {
    KafkaProducer<String, String> producer;
    try (var stream = Producer.class.getClassLoader().getResourceAsStream("producer.properties")) {
      Properties props = new Properties();
      props.load(stream);
      props.setProperty("client.id", "02-producer-" + UUID.randomUUID());

      if (inDocker) {
        props.setProperty("bootstrap.servers", props.getProperty("bootstrap.servers.docker"));
      }

      System.out.println("KafkaProducer properties:");
      System.out.println(props);

      producer = new KafkaProducer<>(props);

      System.out.println("KafkaProducer initialized");
    }

    try {
      for (int i = 0; i < 100000; i++) {
        ProducerRecord<String, String> user =
            new ProducerRecord<>(
                "user-events", // topic
                "user_id_" + i, // key
                "some_value_" + System.nanoTime());
        producer.send(user); // value

        if (i > 0 && i % 100 == 0) {
          String event =
              globalEvents[(int) (Math.random() * globalEvents.length)] + "_" + System.nanoTime();

          ProducerRecord<String, String> global =
              new ProducerRecord<>(
                  "global-events", // topic
                  event);
          producer.send(global); // value
          System.out.println("Producting a global message. Message #" + i);

          producer.flush();
          System.out.println("flushed on " + i);
        }
      }
      producer.flush();
      System.out.println("final flush completed");
    } catch (Throwable throwable) {
      throwable.printStackTrace(System.err);
    } finally {
      producer.close();
      System.out.println("KafkaProducer closed");
      System.exit(0);
    }
  }
}
