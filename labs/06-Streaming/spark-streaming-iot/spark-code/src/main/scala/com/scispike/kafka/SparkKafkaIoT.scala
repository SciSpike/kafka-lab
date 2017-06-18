package com.scispike.kafka


//import com.github.benfradet.spark.kafka010.writer._
//import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.{StringDeserializer, StringSerializer}
import org.apache.spark.SparkConf
import org.apache.spark.streaming.kafka010.ConsumerStrategies.Subscribe
import org.apache.spark.streaming.kafka010.LocationStrategies.PreferConsistent
import org.apache.spark.streaming.kafka010._
import org.apache.spark.streaming._
import org.apache.log4j.{Level, Logger}





case class VehicleStr(id: String, timeUtc: String, speed: String, compassDir: String, longitude: String, latitude: String)





object SparkKafkaIoT {

  def roundAccuracy(coordinate: String, decimalPoints: Int): String = {
    val Array(intPart, decimalPart) = coordinate.split("\\.")
    intPart + "." + decimalPart.substring(0, decimalPoints)
  }

  /*
   * State update function. The state is a collection of keys and values.
   * Key: (id, long, lat)
   * Value: number of times parked
   */
  def updateParkedVehicles(batchTime: Time, key: (String, String, String), value: Option[Long], state: State[Long]):
    Option[((String, String, String), Long)] = {
    val noOfTimesParked = value.getOrElse(0L) + state.getOption.getOrElse(0L)
    val output = (key, noOfTimesParked)
    state.update(noOfTimesParked)
    Some(output)
  }



  def main(args: Array[String]): Unit = {
    println("Spark Kafka IoT Example")
    if (args.length < 3) {
      System.err.println(s"""
                            |Usage: SparkKafkaIoT <brokers> <topics> <interval>
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
    val sparkConf = new SparkConf().setAppName("SparkKafkaIoT")

    // Create streaming context, with batch duration in ms
    val ssc = new StreamingContext(sparkConf, Duration(interval.toLong))
    ssc.checkpoint("./output")

    // Managing state
    val stateSpec = StateSpec.function(updateParkedVehicles _)

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

    // Accuracy for GPS coordinates
    // https://gis.stackexchange.com/questions/8650/measuring-accuracy-of-latitude-and-longitude
    val hundredMeters = 3

    // We round the observations within 100 meters
    val vehicleObservations = lines.map(_.split("\t")).
      map(strings => VehicleStr(strings(0), strings(1), strings(2), strings(3),
        roundAccuracy(strings(4), hundredMeters), roundAccuracy(strings(5), hundredMeters)))

    val parkedVehicles = vehicleObservations.filter(_.speed == "0")

    val parkingCountsRaw = parkedVehicles.map(v => ((v.id, v.longitude, v.latitude), 1)).countByValue()

    // parkingCountsRaw are tuples with the structure:
    // (((id, long, lat), 1), timesParked)


    // Drop the "1" field
    // The structure is now a KV tuple ((id, long, lat), timesParked)
    val parkingCounts = parkingCountsRaw.map(pcr => ((pcr._1._1._1, pcr._1._1._2, pcr._1._1._3), pcr._2))

    // Applying the state update
    val parkingCountsStream = parkingCounts.mapWithState(stateSpec)

    // The state represented as a DStream - we will use it to check the updates
    val parkingSnaphotsStream = parkingCountsStream.stateSnapshots()

    // This will print 10 values from the state DStream
    // Notice how the timesParked for a vehicle at a location accumulate as we process more data
    parkingSnaphotsStream.print()

    // A real app may update a database which feeds a dashboard...

    // Start stream processing
    ssc.start()
    ssc.awaitTermination()
  }

}




