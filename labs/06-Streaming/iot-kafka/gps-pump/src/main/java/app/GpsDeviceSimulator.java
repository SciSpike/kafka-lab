package app;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

public class GpsDeviceSimulator {
  String dataFile;
  Timer timer;
  KafkaProducer<String, String> producer;
  BufferedReader reader;

  public GpsDeviceSimulator(final KafkaProducer<String, String> producer, String dataFile) {
    this.dataFile = dataFile;
    this.producer = producer;
  }

  public void reset() throws IOException {
    if (reader != null) {
      reader.close();
    }
    timer = new Timer();
    reader =
        new BufferedReader(
            new InputStreamReader(
                Objects.requireNonNull(getClass().getResourceAsStream("/" + dataFile))));
  }

  public void start() throws IOException {
    reset();

    timer.scheduleAtFixedRate(
        new TimerTask() {
          @Override
          public void run() {
            try {
              String line = reader.readLine();
              if (line != null) {
                producer.send(new ProducerRecord<>("gps-locations", line));
              } else {
                reset();
              }
              System.out.println("Sent: " + line);
            } catch (Exception e) {
              try {
                reset();
              } catch (Exception e2) {
                System.exit(1);
              }
            }
          }
        },
        2000,
        10);
  }

  public void shutdown() {
    timer.cancel();
  }
}
