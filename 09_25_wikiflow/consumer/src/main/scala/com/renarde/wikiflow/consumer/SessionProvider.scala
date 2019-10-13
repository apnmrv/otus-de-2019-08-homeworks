package com.renarde.wikiflow.consumer

import org.apache.spark.sql.SparkSession

object SessionProvider {
  def create(appName:String): SparkSession = {
    SparkSession.builder()
      .appName(appName)
      .config("spark.driver.memory", "5g")
      .master("local[2]")
      .getOrCreate()
  }
}
