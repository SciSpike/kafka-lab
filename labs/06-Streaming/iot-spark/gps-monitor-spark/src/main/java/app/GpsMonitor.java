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

public class GpsMonitor {
  private static boolean inDocker = new File("/.dockerenv").exists();
  private static Pattern regex = Pattern.compile("\t");

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
        // split lines
        .map(
            record -> {
              var line = record.value();
              System.out.println("splitting: " + line);
              return regex.split(line.trim().toLowerCase());
            })
        // only keep positions where speed is less than 1, meaning "parked"
        .filter(
            array -> {
              String record = Arrays.toString(array);
              try {
                // ensure record contains valid data where expected; should really check that these
                // are valid lats & longs, but we'll just go with it if they're parsable as doubles
                Double.parseDouble(array[4]);
                Double.parseDouble(array[5]);
                if (Double.parseDouble(array[2]) < 1.0) {
                  // then this reading represents a "parked" record
                  System.out.println("keeping parking record: " + record);
                  return true;
                }
                // else ignore
                return false;
              } catch (Exception x) {
                System.out.println("filtering out bad record: " + record);
                return false;
              }
            })
        // convert to pairs where key is word and initial count is 1
        .mapToPair(
            array -> {
              var key =
                  new LocationKey(
                          array[0], Double.parseDouble(array[4]), Double.parseDouble(array[5]))
                      .toString();

              return new Tuple2<>(key, 1L);
            })
        .reduceByKey(Long::sum)
        .print();

    jssc.start();
    jssc.awaitTermination();
  }

  private static Properties getProperties() throws IOException {
    var props = new Properties();
    try (var stream = GpsMonitor.class.getClassLoader().getResourceAsStream("streams.properties")) {

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
