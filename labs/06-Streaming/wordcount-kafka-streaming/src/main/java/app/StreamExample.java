package app;

import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Materialized;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Properties;
import java.util.UUID;
import java.util.regex.Pattern;

public class StreamExample {
  private static boolean inDocker = new File("/.dockerenv").exists();
  private static Pattern regex = Pattern.compile("(\\s|\\.|\\?|!|;|:|-|/|,|\")+");

  public static void main(String[] args) throws Exception {
    var props = getProperties();

    // First we create a stream builder
    var builder = new StreamsBuilder();

    // Next, lets specify which stream we consume from
    KStream<String, String> stream = builder.stream(props.getProperty("topics.input"));

    // This starts the processing topology -- the println statements allow you to see that things are happening
    stream
        // convert each message list of words
        .flatMapValues(val -> {
          System.out.println("flatMapValues:" + val);
          return Arrays.asList(regex.split(val.trim().toLowerCase()));
        })
        // only keep non-blank words
        .filter((__, val) -> {
          System.out.println("filter:" + val);
          return val.trim().length() > 0;
        })
        // we're not really interested in the key in the incoming messages; we only want the values
        .map((__, val) -> {
          System.out.println("map:" + val);
          return new KeyValue<>(val, val);
        })
        // now we need to group them by the word
        .groupByKey()
        // let's keep a ktable count called Counts
        .count(Materialized.as("Counts"))
        // convert the KTable back to a stream
        .toStream()
        // convert values to strings because we're using string serialization
        .mapValues(val -> {
          System.out.println("mapValues:" + val);
          return val.toString();
        })
        // and finally we pipe the output values into the stream-output topic
        .to(props.getProperty("topics.output"));

    // let's hook up to Kafka using the builder and the properties
    KafkaStreams streams = new KafkaStreams(builder.build(), props);
    // and then we can start the stream processing
    streams.start();

    // this is a bit of a hack... most typical would be for the stream processor
    // to run forever or until some condition are met, but for now we run
    // until someone hits enter...
    System.out.println("Press enter to quit the stream processor");
    System.in.read();
    // finally, let's close the stream
    streams.close();
  }

  private static Properties getProperties() throws IOException {
    Properties props = new Properties();
    try (InputStream stream =
        StreamExample.class.getClassLoader().getResourceAsStream("streams.properties")) {

      props.load(stream);

      props.setProperty(
          "application.id", props.getProperty("application.id.prefix") + "-" + UUID.randomUUID());
      props.setProperty("client.id", props.getProperty("application.id"));
      props.setProperty("group.instance.id", props.getProperty("application.id"));

      if (inDocker) {
        props.setProperty("bootstrap.servers", props.getProperty("bootstrap.servers.docker"));
      }

      return props;
    }
  }
}
