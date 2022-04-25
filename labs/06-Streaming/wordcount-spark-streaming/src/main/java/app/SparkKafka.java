package app;

import org.apache.spark.SparkConf;
import org.apache.spark.streaming.Duration;
import org.apache.spark.streaming.api.java.JavaStreamingContext;
import org.apache.spark.streaming.kafka010.ConsumerStrategies;
import org.apache.spark.streaming.kafka010.KafkaUtils;
import org.apache.spark.streaming.kafka010.LocationStrategies;
import scala.Tuple2;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

public class SparkKafka {
  private static boolean inDocker = new File("/.dockerenv").exists();
  private static Pattern regex = Pattern.compile("(\\s|\\.|\\?|!|;|:|-|/|,|\")+");

  public static void main(String[] args) throws IOException, InterruptedException {
    var props = getProperties();

    var conf =
        new SparkConf()
            .setAppName(props.get("application.id.prefix") + "-" + UUID.randomUUID())
            .setMaster((String) props.get("spark.master" + (inDocker ? ".docker" : "")));
    var jssc = new JavaStreamingContext(conf, new Duration(1000));

    var topics = List.of(props.getProperty("topics.input"));

    var stream =
        KafkaUtils.createDirectStream(
            jssc,
            LocationStrategies.PreferConsistent(),
            ConsumerStrategies.<String, String>Subscribe(topics, toMap(props)));

    stream
        // split lines to words
        .flatMap(it -> Arrays.asList(regex.split(it.value().trim().toLowerCase())).iterator())
        // only keep non-blank words, just in case any got in there
        .filter(it -> it.trim().length() > 0)
        // convert to pairs where key is word and initial count is 1
        .mapToPair(it -> new Tuple2<>(it, 1L))
        // update total of occurrences of each word
        .reduceByKey(Long::sum)
        .print();

    jssc.start();
    jssc.awaitTermination();
  }

  private static Properties getProperties() throws IOException {
    var props = new Properties();
    try (var stream = SparkKafka.class.getClassLoader().getResourceAsStream("streams.properties")) {

      props.load(stream);

      props.setProperty(
          "application.id", props.getProperty("application.id.prefix") + "-" + UUID.randomUUID());
      props.setProperty("client.id", props.getProperty("application.id"));
      props.setProperty("group.id", props.getProperty("application.id"));
      props.setProperty("group.instance.id", props.getProperty("application.id"));

      if (inDocker) {
        props.setProperty("bootstrap.servers", props.getProperty("bootstrap.servers.docker"));
      }

      return props;
    }
  }

  private static Map<String, Object> toMap(Properties props) {
    var map = new HashMap<String, Object>(props.size());
    props.keySet().forEach(it -> map.put(it.toString(), props.getProperty(it.toString())));
    return map;
  }
}
