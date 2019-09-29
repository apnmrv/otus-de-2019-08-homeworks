package ru.otus.de_2019_08.json_reader

import org.apache.spark.sql.SparkSession

object App {
  def main(args : Array[String]): Unit = {

    val spark = SparkSession.builder()
      .appName("JSON by Spark DataFrame Reader")
      .master("local[*]")
      .getOrCreate()

    val sc = spark.sparkContext

    val resourcePath = getClass.getResource(
//      "/data_files/github_logs/2019-09-13-6.json"
      "/data_files/winemag-data-130k-v2.json"
    ).getPath

    val bottles = spark.read.json(resourcePath)

//    val ghLog = spark.read.json(resourcePath)
//
//    val eventTypes = ghLog.groupBy("type")
//
//    val pushes = ghLog.filter("type = 'PushEvent'")
//
//    val grouped = pushes.groupBy("actor.login").count

    println("Data schema : ")

    bottles.printSchema()

    bottles.show

//    pushes.printSchema

//    println("Event types : ")
//
//    eventTypes.count().sort("count").show()
//
//    println("all events: " + ghLog.count)
//
//    println("only pushes: " + pushes.count)
//
//    pushes.show(5)
//
//    grouped.show(5)
  }
}
