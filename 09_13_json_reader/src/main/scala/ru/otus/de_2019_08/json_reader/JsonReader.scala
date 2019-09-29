package ru.otus.de_2019_08.json_reader

import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession

object JsonReader {

  val factory:WineBottleFactory = new WineBottleFactory

  val printer:Printer = new Printer

  def main(args : Array[String]): Unit = {
    val dataPath = args(0)

    val spark = SparkSession.builder()
      .appName("JSON by Spark RDD API Reader")
      .master("local[*]")
      .getOrCreate()

    val sc:SparkContext = spark.sparkContext

    val rawData:RDD[String] = sc.textFile(dataPath)

    val bottles:RDD[ABottleOfWine] = rawData.map(createBottle)

    bottles.foreach(bottle => this.printer.print(bottle))

  }

  def createBottle(dataString:String):ABottleOfWine = {
    return this.factory.create(dataString)
  }
}
