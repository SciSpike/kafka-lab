package com.example;

import java.util.Date;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

public class DeviceSim {
	private static final Timer timer = new Timer();
	private final static Random r = new Random();
	public DeviceSim(final String deviceId, final KafkaProducer<String, String> producer) {
		timer.scheduleAtFixedRate(new TimerTask() {

			@Override
			public void run() {
				if (r.nextBoolean()) {
					System.out.println("Produced heartbeat for device " + deviceId);
					producer.send(
							new ProducerRecord<String, String> (
									"device-heartbeat",
									deviceId,
									deviceId + " sent heartbeat at " + new Date().toString()
									));
				}
			}
		}, r.nextInt(10000)+10000, 15*1000);
	}
	public final static void shutdown() {
		timer.cancel();
	}
}
