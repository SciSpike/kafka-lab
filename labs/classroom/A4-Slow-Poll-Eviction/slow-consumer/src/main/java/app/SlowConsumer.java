package app;

import org.apache.kafka.clients.consumer.CommitFailedException;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

/**
 * A deliberately slow consumer used to demonstrate the {@code max.poll.interval.ms} eviction.
 *
 * <p>It "processes" each record by sleeping for {@code processMs} milliseconds, then commits the
 * batch. If a single poll batch ({@code max.poll.records}) takes longer than
 * {@code max.poll.interval.ms} to process, the broker kicks this consumer out of its group, the
 * commit fails, and the batch is redelivered &mdash; so no progress is ever made.
 *
 * <p>Everything is tunable from the command line so you can break it and then fix it:
 * <pre>
 *   -DprocessMs=1000          how long to "process" each record (ms)
 *   -DmaxPollRecords=10       max.poll.records (records returned per poll)
 *   -DmaxPollIntervalMs=5000  max.poll.interval.ms (the deadline)
 *   -Dtopic=poll-test         topic to consume
 * </pre>
 */
public class SlowConsumer {
  private static final boolean inDocker = new File("/.dockerenv").exists();

  public static void main(String[] args) throws IOException {
    long processMs = Long.parseLong(System.getProperty("processMs", "1000"));
    String maxPollRecords = System.getProperty("maxPollRecords", "10");
    String maxPollIntervalMs = System.getProperty("maxPollIntervalMs", "5000");
    String topic = System.getProperty("topic", "poll-test");

    Properties props = new Properties();
    try (var stream = SlowConsumer.class.getClassLoader().getResourceAsStream("consumer.properties")) {
      props.load(stream);
    }
    if (inDocker) {
      System.out.println("We are in docker. Setting the bootstrap server to the docker config.");
      props.setProperty("bootstrap.servers", props.getProperty("bootstrap.servers.docker"));
    }
    props.setProperty("client.id", "slow-consumer-" + UUID.randomUUID());
    // We commit by hand (after processing each batch) so a failed commit is visible.
    props.setProperty("enable.auto.commit", "false");
    props.setProperty("max.poll.records", maxPollRecords);
    props.setProperty("max.poll.interval.ms", maxPollIntervalMs);

    long worstCaseBatchMs = Long.parseLong(maxPollRecords) * processMs;
    System.out.println("============================================================");
    System.out.printf("SlowConsumer: topic=%s  processMs=%d%n", topic, processMs);
    System.out.printf("  max.poll.records      = %s%n", maxPollRecords);
    System.out.printf("  max.poll.interval.ms  = %s%n", maxPollIntervalMs);
    System.out.printf("  worst-case batch took = %d records x %d ms = %d ms%n",
        Long.parseLong(maxPollRecords), processMs, worstCaseBatchMs);
    if (worstCaseBatchMs > Long.parseLong(maxPollIntervalMs)) {
      System.out.println("  >>> batch time EXCEEDS the deadline: expect eviction + commit failures.");
    } else {
      System.out.println("  >>> batch time is within the deadline: expect smooth progress.");
    }
    System.out.println("============================================================");

    KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
    consumer.subscribe(List.of(topic), new ConsumerRebalanceListener() {
      @Override
      public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
        System.out.println(">> REBALANCE: partitions revoked  " + partitions);
      }
      @Override
      public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
        System.out.println(">> REBALANCE: partitions assigned " + partitions);
      }
    });

    long totalCommitted = 0;
    try {
      while (true) {
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
        if (records.isEmpty()) {
          continue;
        }
        long firstOffset = records.iterator().next().offset();
        System.out.printf("Polled %d records (from offset %d); processing (%d ms each)...%n",
            records.count(), firstOffset, processMs);
        for (ConsumerRecord<String, String> record : records) {
          sleep(processMs); // <-- this is "doing the work" for one record
        }
        try {
          consumer.commitSync();
          totalCommitted += records.count();
          System.out.printf("  committed OK -- progress! total processed so far: %d%n", totalCommitted);
        } catch (CommitFailedException e) {
          System.out.println("  !! COMMIT FAILED -- this consumer was kicked out of the group because");
          System.out.println("     processing the batch took longer than max.poll.interval.ms.");
          System.out.println("     " + e.getMessage());
          System.out.printf("     => this batch (from offset %d) will be redelivered and redone. "
              + "Total committed is still %d.%n", firstOffset, totalCommitted);
        }
      }
    } catch (Throwable t) {
      t.printStackTrace(System.err);
    } finally {
      consumer.close();
    }
  }

  private static void sleep(long ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
