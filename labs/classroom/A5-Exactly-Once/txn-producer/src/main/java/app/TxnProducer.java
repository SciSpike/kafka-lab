package app;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.io.File;
import java.util.Properties;

/**
 * A transactional producer used to demonstrate Kafka's exactly-once delivery.
 *
 * <p>It writes a batch of records inside a single Kafka <b>transaction</b>, then either
 * {@code commitTransaction()} or {@code abortTransaction()} — controlled by a flag. A consumer with
 * {@code isolation.level=read_committed} sees the records from a committed transaction and never the
 * records from an aborted one; a {@code read_uncommitted} consumer sees both. That difference is
 * exactly-once delivery made visible.
 *
 * <pre>
 *   -Dtopic=payments    topic to write to
 *   -Dcount=5           how many records in the transaction
 *   -Daction=commit     commit | abort
 *   -Dprefix=ok         value/key prefix (so you can tell batches apart)
 * </pre>
 */
public class TxnProducer {
  private static final boolean inDocker = new File("/.dockerenv").exists();

  public static void main(String[] args) {
    String topic = System.getProperty("topic", "payments");
    int count = Integer.parseInt(System.getProperty("count", "5"));
    String action = System.getProperty("action", "commit");
    String prefix = System.getProperty("prefix", action);

    Properties props = new Properties();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, inDocker ? "broker:29092" : "localhost:9092");
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    // The three settings that make a producer transactional:
    props.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, "txn-demo-producer");
    props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
    props.put(ProducerConfig.ACKS_CONFIG, "all");

    KafkaProducer<String, String> producer = new KafkaProducer<>(props);
    producer.initTransactions();
    System.out.printf("Transaction: writing %d record(s) to '%s' and will %s.%n",
        count, topic, action.toUpperCase());

    try {
      producer.beginTransaction();
      for (int i = 1; i <= count; i++) {
        String value = prefix + "-" + i;
        producer.send(new ProducerRecord<>(topic, value, value));
        System.out.println("  sent (not yet visible to read_committed): " + value);
      }
      // Force the buffered records to the partition log *before* deciding, so that even an aborted
      // batch is physically written (then marked aborted) — that's what read_uncommitted will see.
      producer.flush();
      if ("abort".equalsIgnoreCase(action)) {
        producer.abortTransaction();
        System.out.println("ABORTED: read_uncommitted consumers will see these records; "
            + "read_committed consumers will NOT.");
      } else {
        producer.commitTransaction();
        System.out.println("COMMITTED: these records are now visible to every consumer.");
      }
    } catch (Exception e) {
      producer.abortTransaction();
      e.printStackTrace(System.err);
    } finally {
      producer.close();
    }
  }
}
