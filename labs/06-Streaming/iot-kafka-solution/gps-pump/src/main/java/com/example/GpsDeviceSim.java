package com.example;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

public class GpsDeviceSim {
	String dataFileLocation;
	
	Timer timer;
	KafkaProducer<String, String> producer;
	BufferedReader reader;
	public GpsDeviceSim(final KafkaProducer<String, String> producer, String dataFile) throws FileNotFoundException {
		this.dataFileLocation = dataFile;
		this.producer = producer;
		File f = new File(this.dataFileLocation);
		if (!f.exists() || f.isDirectory() ) 
			throw new FileNotFoundException("Can't open " + this.dataFileLocation);
	}
	public void reset() throws IOException {
		if ( this.reader != null) {
			this.reader.close();
		}
		this.timer = new Timer();
		this.reader = new BufferedReader(new FileReader(this.dataFileLocation));
	}
	public void start() throws FileNotFoundException, IOException {
		this.reset();
		this.timer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				try {
					String line = GpsDeviceSim.this.reader.readLine();
					if (line != null)
						GpsDeviceSim.this.producer.send(new ProducerRecord<String, String>("gps-locations",line));
					else {
						GpsDeviceSim.this.reset();
					}
					System.out.println("Sent: " + line);
				} catch (Exception e) {
					System.out.println("Error reading file");
					try {
						GpsDeviceSim.this.reset();
					}
					catch (Exception e2) {
						System.exit(1);
					}
				}
				
			}
		}, 2000, 10);
	}
	public void shutdown() {
		timer.cancel();
	}
}
