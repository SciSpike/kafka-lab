package com.scispike.kafka


//import com.github.benfradet.spark.kafka010.writer._
//import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.{StringDeserializer, StringSerializer}
import org.apache.spark.SparkConf
import org.apache.spark.streaming.kafka010.ConsumerStrategies.Subscribe
import org.apache.spark.streaming.kafka010.LocationStrategies.PreferConsistent
import org.apache.spark.streaming.kafka010._
import org.apache.spark.streaming.{Duration, Seconds, StreamingContext}

import org.apache.log4j.{Level, Logger}

object SparkKafka {

  def main(args: Array[String]): Unit = {
    println("Spark Kafka Example - Word count from a Kafka stream")
    if (args.length < 3) {
      System.err.println(s"""
                            |Usage: SparkKafka <brokers> <topics> <interval>
                            |  <brokers> is a list of one or more Kafka brokers: broker1,broker2
                            |  <topics> is a list of one or more kafka topics to consume from
                            |  <interval> interval duration (ms)
                            |
        """.stripMargin)
      System.exit(1)
    }

    // Show only errors in console
    val rootLogger = Logger.getRootLogger()
    rootLogger.setLevel(Level.ERROR)

    // Consume command line parameters
    val Array(brokers, topics, interval) = args

    // Create Spark configuration
    val sparkConf = new SparkConf().setAppName("SparkKafka")

    // Create streaming context, with batch duration in ms
    val ssc = new StreamingContext(sparkConf, Duration(interval.toLong))
    ssc.checkpoint("./output")


    // Create a set of topics from a string
    val topicsSet = topics.split(",").toSet

    // Define Kafka parameters
    val kafkaParams = Map[String, Object](
      "bootstrap.servers" -> brokers,
      "key.deserializer" -> classOf[StringDeserializer],
      "value.deserializer" -> classOf[StringDeserializer],
      "group.id" -> "use_a_separate_group_id_for_each_stream",
      "auto.offset.reset" -> "latest",
      "enable.auto.commit" -> (false: java.lang.Boolean))


    // Create a Kafka stream
    val stream = KafkaUtils.createDirectStream[String, String](
      ssc, PreferConsistent, Subscribe[String, String](topicsSet,kafkaParams))

    // Get messages - lines of text from Kafka
    val lines = stream.map(consumerRecord => consumerRecord.value)

    // Split lines into words
    val words = lines.flatMap(_.split(" "))

    // Map every word to a tuple
    val wordMap = words.map(word => (word, 1))

    // Count occurrences of each word
    val wordCount = wordMap.reduceByKey(_ + _)

    //Print the word count
    wordCount.print()

    // Start stream processing
    ssc.start()
    ssc.awaitTermination()
  }

}

