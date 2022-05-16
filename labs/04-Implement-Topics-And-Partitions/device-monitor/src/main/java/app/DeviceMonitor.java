package app;

import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

public class DeviceMonitor {
  private static final ConcurrentHashMap<String, Date> lastSeenMap = new ConcurrentHashMap<>();
  private static final ConcurrentSkipListSet<String> offlineDevices = new ConcurrentSkipListSet<>();

  private static boolean inDocker = new File("/.dockerenv").exists();

  public static void main(String[] args) throws IOException {
    Duration timeout = Duration.ofSeconds(1);

    var consumer = createConsumer();
    var tuple = createProducer();
    var producer = tuple._1;
    var topic = tuple._2;

    setupMonitoring(producer, topic);

    while (true) {
      var records = consumer.poll(timeout);

      for (var record : records) {
        String key = record.key();
        if (!lastSeenMap.containsKey(record.key())) {
          producer.send(createOnlineMessage(key, topic));
        }
        lastSeenMap.put(record.key(), new Date());
        System.out.println("Received heartbeat from: " + key + " value: " + record.value());

        if (offlineDevices.contains(key)) {
          offlineDevices.remove(key);
          System.out.println("Device back online: " + key);
          producer.send(createOnlineMessage(key, topic));
        }
      }
    }
  }

  private static void setupMonitoring(final KafkaProducer<String, String> producer, String topic) {
    final Timer timer = new Timer();
    timer.scheduleAtFixedRate(
        new TimerTask() {
          @Override
          public void run() {
            System.out.println("Checking devices...");
            Enumeration<String> e = lastSeenMap.keys();
            while (e.hasMoreElements()) {
              String key = e.nextElement();
              if (offlineDevices.contains(key)) {
                System.out.println("Device is still offline, no message sent: " + key);
              } else if (heartbeatMissing(lastSeenMap.get(key))) {
                offlineDevices.add(key);
                System.out.println(
                    "Device has not been heard from for some time. Producing a new event: " + key);
                producer.send(createOfflineMessage(key, topic));
              }
            }
            System.out.println("Finished checking devices...");
          }
        },
        60 * 1000,
        5 * 1000);
  }

  private static KafkaConsumer<String, String> createConsumer() throws IOException {
    try (var stream =
        DeviceMonitor.class.getClassLoader().getResourceAsStream("consumer.properties")) {
      Properties props = new Properties();
      props.load(stream);
      props.setProperty("client.id", "04-consumer-" + UUID.randomUUID());
      props.setProperty("group.instance.id", props.getProperty("client.id"));

      if (inDocker) {
        props.setProperty("bootstrap.servers", props.getProperty("bootstrap.servers.docker"));
      }

      var consumer = new KafkaConsumer<String, String>(props);

      consumer.subscribe(Arrays.asList(props.getProperty("topics.heartbeat")));

      return consumer;
    }
  }

  private static Tuple<KafkaProducer<String, String>, String> createProducer() throws IOException {
    try (var stream =
        DeviceMonitor.class.getClassLoader().getResourceAsStream("producer.properties")) {
      Properties props = new Properties();
      props.load(stream);
      props.setProperty("client.id", "04-producer-" + UUID.randomUUID());

      if (inDocker) {
        props.setProperty("bootstrap.servers", props.getProperty("bootstrap.servers.docker"));
      }

      return new Tuple<>(new KafkaProducer<>(props), props.getProperty("topics.device"));
    }
  }

  private static boolean heartbeatMissing(Date lastDate) {
    return lastDate.getTime() + (60 * 1000) < new Date().getTime();
  }

  private static ProducerRecord<String, String> createOfflineMessage(String key, String topic) {
    return new ProducerRecord<>(
        topic, key, key + " offline since " + lastSeenMap.get(key).toString());
  }

  private static ProducerRecord<String, String> createOnlineMessage(String key, String topic) {
    return new ProducerRecord<>(topic, key, key + " was online @ " + new Date());
  }

  public static class Tuple<T1,T2> {
    public T1 _1;
    public T2 _2;

    public Tuple(T1 t1, T2 t2) {
      this._1 = t1;
      this._2 = t2;
    }
  }
}
