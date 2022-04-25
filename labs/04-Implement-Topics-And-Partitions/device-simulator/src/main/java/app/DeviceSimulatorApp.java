package app;

import org.apache.kafka.clients.producer.KafkaProducer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static java.lang.String.format;

public class DeviceSimulatorApp {

  public static void main(String[] args) {
    try {
      var props = getProperties();
      var producer = new KafkaProducer<String, String>(props);
      createSimulators(producer, Integer.parseInt(props.getProperty("simulator.count").trim()));

      System.out.println("Press enter to quit");
      System.in.read();
    } catch (Throwable throwable) {
      throwable.printStackTrace(System.err);
    } finally {
      DeviceSimulator.shutdown();
    }
  }

  private static boolean inDocker = new File("/.dockerenv").exists();

  private static List<DeviceSimulator> createSimulators(
      KafkaProducer<String, String> producer, int count) {

    var simulators = new ArrayList<DeviceSimulator>();

    for (int i = 1; i <= count; i++) {
      simulators.add(new DeviceSimulator(format("Heart monitor %d", i), producer));
      simulators.add(new DeviceSimulator(format("Scale %d", i), producer));
    }

    return simulators;
  }

  private static Properties getProperties() throws IOException {
    Properties props;

    try (var stream =
        DeviceSimulatorApp.class.getClassLoader().getResourceAsStream("producer.properties")) {
      props = new Properties();
      props.load(stream);
      props.setProperty("client.id", "04-producer-" + UUID.randomUUID());

      if (inDocker) {
        props.setProperty("bootstrap.servers", props.getProperty("bootstrap.servers.docker"));
      }

      System.out.println("KafkaProducer properties:");
      System.out.println(props);
    }

    return props;
  }
}
