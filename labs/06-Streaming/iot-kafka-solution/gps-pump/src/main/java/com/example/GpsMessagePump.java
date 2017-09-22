package com.example;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.apache.kafka.clients.producer.KafkaProducer;

import com.google.common.io.Resources;

public class GpsMessagePump {

	public static void main(String[] args) throws IOException {
		KafkaProducer<String, String> producer;
		producer = createProducer();
		GpsDeviceSim sim = creatAndStartSimulator(producer, args[0]);

		try {
			System.out.println("Press enter to quit");
			System.in.read();
		} catch (Throwable throwable) {
			System.out.println(throwable.getStackTrace());
		} finally {
			sim.shutdown();
			producer.close();
		}

	}

	private static GpsDeviceSim creatAndStartSimulator(KafkaProducer<String, String> producer, String fileLocation) {
		try {
			File f = new File(fileLocation);
			if (!f.exists() || f.isDirectory() ) 
				throw new FileNotFoundException("Can't open " + fileLocation);
			GpsDeviceSim sim =  new GpsDeviceSim(producer, fileLocation);
			sim.start();
			return sim;
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
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
