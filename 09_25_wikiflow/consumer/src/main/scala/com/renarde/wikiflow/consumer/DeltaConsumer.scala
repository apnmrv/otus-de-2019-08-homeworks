package com.renarde.wikiflow.consumer

import com.typesafe.scalalogging.LazyLogging
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.streaming.Trigger

object DeltaConsumer extends App with LazyLogging {

  val appName: String = "delta-consumer"
  val MAX_FILES_PER_TRIGGER = 3;
  val spark: SparkSession = SessionProvider.create(appName)

  spark.sparkContext.setLogLevel("WARN")

  logger.info("Initializing Structured consumer")

  spark.readStream
    .format("delta")
    .load("/storage/analytics")
    .orderBy("window")
    .writeStream
    .format("console")
    .trigger(Trigger.ProcessingTime("3 seconds"))
    .start()

  spark.streams.awaitAnyTermination()
}
