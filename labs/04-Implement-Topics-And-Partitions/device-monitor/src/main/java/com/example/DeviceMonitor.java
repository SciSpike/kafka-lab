package com.example;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

import com.google.common.io.Resources;

public class DeviceMonitor {
	private final static ConcurrentHashMap<String, Date> lastSeenMap = new ConcurrentHashMap<String, Date>();
	private final static ConcurrentSkipListSet<String> offlineDevices = new ConcurrentSkipListSet<String>();

	public static void main(String[] args) throws IOException {

		final KafkaConsumer<String, String> consumer = setupConsumer();
		final KafkaProducer<String, String> producer = setupProducer();
		setupMonitoring(producer);

		while (true) {
			final ConsumerRecords<String, String> records = consumer.poll(1000);
			for (ConsumerRecord<String, String> record : records) {
				final String key = record.key();
				lastSeenMap.put(record.key(), new Date());
				System.out.println("Received heartbeat from: " + key + " value: " + record.value());
				if (offlineDevices.contains(key)) {
					offlineDevices.remove(key);
					System.out.println("Device back online: " + key);
					producer.send(createOnlineMessage(key));
				}

			}
		}

	}

	private static void setupMonitoring(final KafkaProducer<String, String> producer) {
		final Timer timer = new Timer();
		timer.scheduleAtFixedRate(new TimerTask() {

			@Override
			public void run() {
				System.out.println("Checking devices...");
				Enumeration<String> e = lastSeenMap.keys();
				while (e.hasMoreElements()) {
					String key = e.nextElement();
					if (offlineDevices.contains(key)) {
						System.out.println("Device is still offline, no message sent: " + key);
					} else if (heartBeatMissing(lastSeenMap.get(key))) {
						offlineDevices.add(key);
						System.out
								.println("Device has not been heard from for some time. Producing a new event: " + key);
						producer.send(createOfflineMessage(key));
					}
				}
				System.out.println("Finished checking devices...");
			}
		}, 60 * 1000, 60 * 1000);
	}

	private static KafkaConsumer<String, String> setupConsumer() throws IOException {
		KafkaConsumer<String, String> consumer;
		try (InputStream props = Resources.getResource("consumer.properties").openStream()) {
			Properties properties = new Properties();
			properties.load(props);
			consumer = new KafkaConsumer<String, String>(properties);
		}
		consumer.subscribe(Arrays.asList("device-heartbeat"));
		return consumer;
	}

	private static KafkaProducer<String, String> setupProducer() throws IOException {
		KafkaProducer<String, String> producer;
		try (InputStream props = Resources.getResource("producer.properties").openStream()) {
			Properties properties = new Properties();
			properties.load(props);
			producer = new KafkaProducer<>(properties);
		}
		return producer;
	}

	private static boolean heartBeatMissing(Date lastDate) {
		return lastDate.getTime() + (60 * 1000) < new Date().getTime();
	}

	private static ProducerRecord<String, String> createOfflineMessage(String key) {
		return new ProducerRecord<String, String>("device-event", key,
				key + " offline since " + lastSeenMap.get(key).toString());
	}

	private static ProducerRecord<String, String> createOnlineMessage(String key) {
		return new ProducerRecord<String, String>("device-event", key, key + " is back online");
	}
}
