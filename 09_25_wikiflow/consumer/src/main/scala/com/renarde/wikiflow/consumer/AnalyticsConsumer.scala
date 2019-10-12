package com.renarde.wikiflow.consumer

import com.typesafe.scalalogging.LazyLogging
import org.apache.spark.sql.{DataFrame, SparkSession}
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types.{IntegerType, TimestampType}

object AnalyticsConsumer extends App with LazyLogging {

  val WINDOW_DURATION: String = "10 minutes"
  val SLIDE_DURATION: String = "5 minutes"
  val APP_NAME: String = "analytics-consumer-example"

  val spark: SparkSession = SessionProvider.create(APP_NAME)

  import spark.implicits._

  spark.sparkContext.setLogLevel("WARN")

  logger.info("Initializing Structured consumer")

  val inputStream = spark.readStream
    .format("kafka")
    .option("kafka.bootstrap.servers", "kafka:9092")
    .option("subscribe", "wikiflow-topic")
    .option("startingOffsets", "earliest")
    .load()

  // please edit the code below
  //  val transformedStream: DataFrame = inputStream
  val dataStructure = SchemaFactory.kafkaMessageSchema()

  val transformedStream: DataFrame = inputStream
    .select(
      $"key".cast("string").as("event_key"),
      $"value".cast("string").as("event_data")
    ).as[(String, String)]
    .filter($"event_data".isNotNull)
    .select(from_json($"event_data", dataStructure).as("event_data"))
    .filter($"event_data.bot" =!= true)
    .select(
      $"event_data.timestamp".cast(IntegerType).cast(TimestampType).as("event_time"),
      $"event_data.type".as("event_type")
     )

  val streamData = transformedStream
    .withWatermark("event_time", WINDOW_DURATION)
    .groupBy(
      window($"event_time", WINDOW_DURATION, SLIDE_DURATION),
      $"event_type"
    )
    .count()

  streamData.writeStream
//    .format("console")
//    .outputMode("complete")
//    .option("mergeSchema", "true")
//    .option("truncate", "false")
//    .trigger(Trigger.ProcessingTime("3 seconds"))
//    .start()
    .format("delta")
    .outputMode("append")
    .option("mergeSchema", "true")
    .option("overwriteSchema", "true")
    .option("checkpointLocation", "/storage/analytics.checkpoint/")
    .option("path", "/storage/analytics")
    .start()

  spark.streams.awaitAnyTermination()
}
