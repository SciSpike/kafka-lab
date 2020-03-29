package com.example;

import java.util.Arrays;
import java.util.Locale;
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
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Materialized;

public class StreamExample {

	public static void main(String[] args) throws Exception {
		Properties props = new Properties();
		// This is the consumer ID. Kafka keeps track of where you are in the stream (in Zoomaker) for each consumer group
		props.put(StreamsConfig.APPLICATION_ID_CONFIG, "sample-stream-count");
		// Where is zookeeper?
		props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
		// Default key serializer
		props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
		// Default value serializer
		props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());

		// setting offset reset to earliest so that we can re-run the the example code
		// with the same pre-loaded data
		// Note: To re-run the example, you need to use the offset reset tool:
		// https://cwiki.apache.org/confluence/display/KAFKA/Kafka+Streams+Application+Reset+Tool
		props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

		// First we create a stream builder
		StreamsBuilder bld = new StreamsBuilder();

		// Next, lets specify which stream we consume from
		KStream<String, String> source = bld.stream("stream-input");

		// This starts the processing topology
		source
			// convert each message list of words //List<string>
			.flatMapValues(value -> {
				//System.out.println("value = "+value);
				//System.out.println("return "+Arrays.asList(value.toLowerCase(Locale.getDefault()).split(" ")));
				return Arrays.asList(value.toLowerCase(Locale.getDefault()).split(" "));
			})
			// We're not really interested in the key in the incoming messages, we only want the values
			.map( (key,value) -> new KeyValue<>(value, value))
			// now we need to group them by key
			.groupByKey()
			// let's keep a table count called Counts"
			.count(Materialized.as("Counts"))
			// next we map the counts into strings to make serialization work
			.mapValues(value -> {
				System.out.println("Map values == "+value);
				return value.toString();
			})
			.toStream()
			// and finally we pipe the output into the stream-output topic
			.to("stream-output");
		
		// let's hook up to Kafka using the builder and the properties
		KafkaStreams streams = new KafkaStreams(bld.build(), props);
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
