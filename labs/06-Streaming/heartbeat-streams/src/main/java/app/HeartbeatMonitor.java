package app;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.processor.PunctuationType;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.Stores;

import java.io.File;
import java.time.Duration;
import java.util.Properties;

/**
 * The heartbeat monitor from the Chapter 3 "implement topics & partitions" lab, re-implemented in
 * Kafka Streams.
 *
 * <p>The plain-Java {@code device-monitor} keeps its "when did I last hear from each device?" state
 * in an in-memory {@code HashMap}, so a crash/restart wipes it — the monitor forgets every device it
 * was watching and can no longer tell that a silent one has gone offline.
 *
 * <p>This version keeps the same state in two <b>persistent, changelog-backed</b> state stores. Kafka
 * Streams logs every change to a compacted changelog topic, so on restart the stores are <b>restored
 * automatically</b> and the monitor carries on exactly where it left off. That's the whole point: a
 * Streams app's state survives failure.
 *
 * <p>Tunable so the demo is quick:
 * <pre>
 *   -DofflineAfterMs=20000   declare a device offline after this long with no heartbeat
 *   -DcheckEveryMs=5000      how often to scan for missing heartbeats
 * </pre>
 */
public class HeartbeatMonitor {
  private static final boolean inDocker = new File("/.dockerenv").exists();
  private static final String SEEN = "last-seen-store";
  private static final String STATUS = "status-store";
  private static final long OFFLINE_AFTER_MS = Long.parseLong(System.getProperty("offlineAfterMs", "20000"));
  private static final long CHECK_EVERY_MS = Long.parseLong(System.getProperty("checkEveryMs", "5000"));

  public static void main(String[] args) {
    Properties props = new Properties();
    props.put(StreamsConfig.APPLICATION_ID_CONFIG, "heartbeat-streams-monitor");
    props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, inDocker ? "broker:29092" : "localhost:9092");
    props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
    props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());
    props.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, "2000");

    StreamsBuilder builder = new StreamsBuilder();
    builder.addStateStore(Stores.keyValueStoreBuilder(
        Stores.persistentKeyValueStore(SEEN), Serdes.String(), Serdes.Long()));
    builder.addStateStore(Stores.keyValueStoreBuilder(
        Stores.persistentKeyValueStore(STATUS), Serdes.String(), Serdes.String()));

    builder.stream("device-heartbeat",
            Consumed.with(Serdes.String(), Serdes.String())
                .withOffsetResetPolicy(Topology.AutoOffsetReset.LATEST))
        .process(HeartbeatProcessor::new, SEEN, STATUS)
        .to("device-event", Produced.with(Serdes.String(), Serdes.String()));

    KafkaStreams streams = new KafkaStreams(builder.build(), props);
    streams.setStateListener((newState, oldState) ->
        System.out.println("[state] " + oldState + " -> " + newState));
    streams.setUncaughtExceptionHandler(e -> {
      System.out.println("[ERROR] " + e);
      e.printStackTrace(System.out);
      return org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler
          .StreamThreadExceptionResponse.SHUTDOWN_CLIENT;
    });
    Runtime.getRuntime().addShutdownHook(new Thread(streams::close));
    System.out.printf("Starting heartbeat Streams monitor (offline after %d ms, checking every %d ms)...%n",
        OFFLINE_AFTER_MS, CHECK_EVERY_MS);
    streams.start();
  }

  static class HeartbeatProcessor implements Processor<String, String, String, String> {
    private ProcessorContext<String, String> ctx;
    private KeyValueStore<String, Long> seen;
    private KeyValueStore<String, String> status;

    @Override
    public void init(ProcessorContext<String, String> context) {
      this.ctx = context;
      this.seen = context.getStateStore(SEEN);
      this.status = context.getStateStore(STATUS);

      // How many devices did we remember across the (re)start? This is the headline of the lab.
      long restored = 0;
      try (var it = seen.all()) {
        while (it.hasNext()) {
          it.next();
          restored++;
        }
      }
      System.out.println(">> Restored last-seen state for " + restored
          + " device(s) from the changelog topic.");

      context.schedule(Duration.ofMillis(CHECK_EVERY_MS), PunctuationType.WALL_CLOCK_TIME, this::checkForSilentDevices);
    }

    @Override
    public void process(Record<String, String> record) {
      String device = record.key();
      if (device == null) return;
      seen.put(device, System.currentTimeMillis());
      if ("OFFLINE".equals(status.get(device))) {
        status.put(device, "ONLINE");
        System.out.println("Device back ONLINE: " + device);
        ctx.forward(new Record<>(device, device + " is back ONLINE", record.timestamp()));
      } else {
        status.putIfAbsent(device, "ONLINE");
      }
    }

    private void checkForSilentDevices(long now) {
      long tracked = 0;
      try (var it = seen.all()) {
        while (it.hasNext()) {
          var entry = it.next();
          tracked++;
          long ageMs = now - entry.value;
          boolean alreadyOffline = "OFFLINE".equals(status.get(entry.key));
          if (ageMs > OFFLINE_AFTER_MS && !alreadyOffline) {
            status.put(entry.key, "OFFLINE");
            System.out.println("Device OFFLINE: " + entry.key + " (last heartbeat " + (ageMs / 1000) + "s ago)");
            ctx.forward(new Record<>(entry.key,
                entry.key + " is OFFLINE (last heartbeat " + (ageMs / 1000) + "s ago)", now));
          }
        }
      }
      System.out.println("Checking devices... currently tracking " + tracked + " device(s).");
    }
  }
}
