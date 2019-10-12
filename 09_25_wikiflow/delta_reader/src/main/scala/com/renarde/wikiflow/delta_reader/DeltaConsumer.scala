package com.renarde.wikiflow.delta_reader

import com.typesafe.scalalogging.LazyLogging
import org.apache.spark.sql.SparkSession

object DeltaConsumer extends App with LazyLogging {

  val appName: String = "delta-consumer"
  val MAX_FILES_PER_TRIGGER = 3;
  val spark: SparkSession = SessionProvider.create(appName)

  spark.sparkContext.setLogLevel("WARN")

  logger.info("Initializing Structured consumer")

  spark.readStream
    .format("delta")
    .load("/storage/analytics")
    .writeStream
    .format("console")
    .option("mergeSchema", "true")
    .option("truncate", "false")
    .start()

  spark.streams.awaitAnyTermination()
}
