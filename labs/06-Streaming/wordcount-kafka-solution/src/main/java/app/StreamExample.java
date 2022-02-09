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

public class StreamExample {
  private static boolean inDocker = new File("/.dockerenv").exists();

  public static void main(String[] args) throws Exception {
    Properties props = getProperties();

    // First we create a stream builder
    StreamsBuilder builder = new StreamsBuilder();

    // Next, lets specify which stream we consume from
    KStream<String, String> stream = builder.stream(props.getProperty("topics.input"));

    // This starts the processing topology
    stream
        // convert each message list of words
        .flatMapValues(
            it -> {
              return Arrays.asList(it.trim().toLowerCase().split("(\\s|\\.|\\?|!|;|:|-|,|\")+"));
            })
        // only keep non-blank words
        .filter(
            (__, value) -> {
              value = value.trim();
              return value.length() > 0;
            })
        // We're not really interested in the key in the incoming messages; we only want the values
        .map((__, value) -> {
          return new KeyValue<>(value, value);
        })
        // now we need to group them by the word
        .groupByKey()
        // let's keep a table count called Counts"
        .count(Materialized.as("Counts"))
        // next we map the counts into strings to make serialization work
        .mapValues(Object::toString)
        .toStream()
        // and finally we pipe the output into the stream-output topic
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
