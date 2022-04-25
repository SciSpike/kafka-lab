package app;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

public class DeviceSimulator {
  private static Timer timer;
  private static final Random r = new Random();
  private static final DateTimeFormatter formatter =
      DateTimeFormatter.ISO_DATE_TIME.withZone(ZoneId.of("Z"));

  public DeviceSimulator(final String deviceId, final KafkaProducer<String, String> producer) {
    if (timer == null) {
      synchronized (DeviceSimulator.class) {
        if (timer == null) {
          timer = new Timer();
        }
      }
    }

    timer.scheduleAtFixedRate(
        new TimerTask() {
          @Override
          public void run() {
            if (r.nextBoolean()) {
              System.out.println("Produced heartbeat for device " + deviceId);
              producer.send(
                  new ProducerRecord<>(
                      "device-heartbeat", deviceId, formatter.format(Instant.now())));
            }
          }
        },
        r.nextInt(10000) + 10000,
        15 * 1000);
  }

  public static void shutdown() {
    if (timer != null) {
      timer.cancel();
    }
  }
}
