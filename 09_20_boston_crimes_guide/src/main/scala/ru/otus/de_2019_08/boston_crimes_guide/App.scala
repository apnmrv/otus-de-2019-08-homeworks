package ru.otus.de_2019_08.boston_crimes_guide

import org.apache.spark.sql.SparkSession

object App {
  def main(args : Array[String]): Unit = {

    val spark = SparkSession.builder()
      .appName("Boston Crimes Guide")
      .master("local[*]")
      .getOrCreate()

    val sc = spark.sparkContext

    val resourcePath = getClass.getResource("/data_files/crimes_in_boston").getPath

    val offenceCodesFilePath = resourcePath + "/offense_codes.csv"

    val crimeFilePath = resourcePath + "/crime.csv"

    val offenceCodesDF = spark.read.format("csv")
      .option("inferSchema", "true")
      .option("header","true")
      .load(offenceCodesFilePath)

    val crimeDF = spark.read.format("csv")
      .option("inferSchema", "true")
      .option("header","true")
      .load(crimeFilePath)

    offenceCodesDF.show()

    crimeDF.show()

  }
}

