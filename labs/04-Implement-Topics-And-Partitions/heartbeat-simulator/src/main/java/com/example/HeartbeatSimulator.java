package com.example;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.apache.kafka.clients.producer.KafkaProducer;

import com.google.common.io.Resources;

public class HeartbeatSimulator {

	public static void main(String[] args) throws IOException {
		KafkaProducer<String, String> producer;
		producer = createProducer();
		createSimulators(producer);

		try {
			System.out.println("Press enter to quit");
			System.in.read();
		} catch (Throwable throwable) {
			System.out.println(throwable.getStackTrace());
		} finally {
			DeviceSim.shutdown();
			producer.close();
		}

	}

	private static DeviceSim[] createSimulators(KafkaProducer<String, String> producer) {
		 return new DeviceSim[] {
				new DeviceSim("Heart monitor 1", producer),
				new DeviceSim("Scale 1", producer),
				new DeviceSim("Heart monitor 2", producer),
				new DeviceSim("Scale 2", producer),
				new DeviceSim("Heart monitor 3", producer),
				new DeviceSim("Scale 3", producer),
				new DeviceSim("Heart monitor 4", producer),
				new DeviceSim("Scale 4", producer),
				new DeviceSim("Heart monitor 5", producer),
				new DeviceSim("Scale 5", producer),
				new DeviceSim("Heart monitor 6", producer),
				new DeviceSim("Scale 6", producer),
				new DeviceSim("Heart monitor 7", producer),
				new DeviceSim("Scale 7", producer),
		};
	}

	private static KafkaProducer<String, String> createProducer() throws IOException {
		KafkaProducer<String, String> producer;
		try (InputStream props = Resources.getResource("producer.properties").openStream()) {
			Properties properties = new Properties();
			properties.load(props);
			producer = new KafkaProducer<>(properties);
		}
		return producer;
	}
}
