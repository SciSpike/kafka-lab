package app;

import org.apache.kafka.clients.producer.KafkaProducer;

import java.io.File;
import java.io.IOException;
import java.util.Properties;
import java.util.UUID;

// kafka-topics.sh --bootstrap-server kafka:9092 --create --topic gps-locations

public class GpsDeviceSimulatorApp {
  private static boolean inDocker = new File("/.dockerenv").exists();

  public static void main(String[] args) {
    try {
      var props = getProperties();
      var producer = new KafkaProducer<String, String>(props);
      var device = new GpsDeviceSimulator(producer, args.length == 0 ? "data.tsv" : args[0]);

      device.start();

      System.out.println("Press enter to quit");
      System.in.read();

      device.shutdown();
    } catch (Throwable throwable) {
      throwable.printStackTrace(System.err);
    }
  }

  private static Properties getProperties() throws IOException {
    Properties props;

    try (var stream =
        GpsDeviceSimulatorApp.class.getClassLoader().getResourceAsStream("producer.properties")) {
      props = new Properties();
      props.load(stream);
      props.setProperty("client.id", "06-producer-" + UUID.randomUUID());

      if (inDocker) {
        props.setProperty("bootstrap.servers", props.getProperty("bootstrap.servers.docker"));
      }

      System.out.println("KafkaProducer properties:");
      System.out.println(props);
    }

    return props;
  }
}
