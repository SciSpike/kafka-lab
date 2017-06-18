package com.example;

import com.google.common.io.Resources;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Properties;

public class Producer {

    private static String[] global_events = { "maintenance_begin", "maintenance_end", "plan_removed", "plan_added", "sale_begin", "sale_end" };

    public static void main(String[] args) throws IOException {
        KafkaProducer<String, String> producer;
        try (InputStream props = Resources.getResource("producer.properties").openStream()) {
            Properties properties = new Properties();
            properties.load(props);
            producer = new KafkaProducer<>(properties);
        }

        try {
            for (int i = 0; i < 100000; i++) {
                producer.send(new ProducerRecord<String, String>(
                      "user-events", // topic
                      "user_id_" + i, // key
                      "some_value_" + System.nanoTime())); // value

                if (i % 100 == 0) {
                    String event = global_events[(int) (Math.random() * global_events.length)] + "_" + System.nanoTime();

                    producer.send(new ProducerRecord<String, String>(
                        "global-events", // topic
                        event)); // value

                    producer.flush();
                    System.out.println("Sent message number " + i);
                }
            }
        } catch (Throwable throwable) {
            System.out.println(throwable.getStackTrace());
        } finally {
            producer.close();
        }

    }
}
