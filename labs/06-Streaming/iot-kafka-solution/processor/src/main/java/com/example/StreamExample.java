package com.example;

import java.util.Properties;

/**
 * This example has been reworked from one of the sample test applications
 * provided by Kafka in their test suite.
 *
 */

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KStreamBuilder;

public class StreamExample {

	public static void main(String[] args) throws Exception {
		Properties props = new Properties();
		// This is the consumer ID. Kafka keeps track of where you are in the stream (in Zoomaker) for each consumer group
		props.put(StreamsConfig.APPLICATION_ID_CONFIG, "frequent-parking");
		// Where is zookeeper?
		props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
		// Default key serializer
		props.put(StreamsConfig.KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
		// Default value serializer
		props.put(StreamsConfig.VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());

		// setting offset reset to earliest so that we can re-run the the example code
		// with the same pre-loaded data
		// Note: To re-run the example, you need to use the offset reset tool:
		// https://cwiki.apache.org/confluence/display/KAFKA/Kafka+Streams+Application+Reset+Tool
		props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

		// First we create a stream builder
		KStreamBuilder bld = new KStreamBuilder();

		// Next, lets specify which stream we consume from
		KStream<String, String> source = bld.stream("gps-locations");

		// This starts the processing topology
		source
			// Convert the incoming tab delimited entry into an array of word
			.map( (key,value) -> new KeyValue<>(key, value.split("\t")))
			// filter out those locates where speed is greater than 0
			.filter((key,value)-> value.length > 5 && Double.parseDouble(value[2]) == 0.0)
			// now we need to group them by key
			.map((key,value) -> new KeyValue<>(new LocationKey(value[0], Double.parseDouble(value[4]), Double.parseDouble(value[5])).toString(), value[0]))
			.groupByKey()
			// let's keep a table count called Counts"
			.count("ParkingCounts")
//			.foreach((key,value) -> System.out.println(key))
			// filter out those where we only seen the vehicle parked 2 or fewer times
//			.filter((key, value) -> value < 3)
			.mapValues((value) -> Long.toString(value))
			// and finally we pipe the output into the stream-output topic
			.to("frequent-parking");

		// let's hook up to Kafka using the builder and the properties
		KafkaStreams streams = new KafkaStreams(bld, props);
		// and then... we can start the stream processing
		streams.start();

		// this is a bit of a hack... most typical would be for the stream processor
		// to run forever or until some condition are met, but for now we run
		// until someone hits enter...
		System.out.println("Press enter to quit the stream processor");
		System.in.read();
		// finally, let's close the stream
		streams.close();
	}
}
