package com.example

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.expressions.Window
import org.apache.spark.sql.functions._

object BostonCrimesMap {
  def main(args : Array[String]): Unit = {

    val spark = SparkSession
      .builder()
      .appName("Boston Crimes Map")
      .master("local[*]")
      .getOrCreate()

    val resourcePath = getClass
      .getResource("/data_files/crimes_in_boston")
      .getPath

    val offenceCodesFilePath = resourcePath + "/offense_codes.csv"

    val crimesFilePath = resourcePath + "/crime.csv"

    val codesDF = spark
      .read
      .option("header", "true")
      .option("inferSchema", "true")
      .csv(offenceCodesFilePath)
      .toDF()

    val crimesDF = spark
      .read
      .option("header", "true")
      .option("inferSchema", "true")
      .csv(crimesFilePath)
      .toDF()

    val crimesStatsPerDistrict = crimesDF
      .groupBy("DISTRICT")
      .agg(
        count("INCIDENT_NUMBER").as("crimes_total"),
        avg("lat").as("lat"),
        avg("long").as("lng")
      )
      .orderBy("crimes_total")

    val crimeTypesCountPerDistrictDF = crimesDF
      .join(codesDF, codesDF("CODE") === crimesDF("OFFENSE_CODE"), "inner")
      .groupBy(crimesDF("DISTRICT"), codesDF("NAME"))
      .agg(count("*") as ("CRIME_TYPE_COUNT_PER_DISTRICT"))

    val crimeTypesRankPerDistrictWindow = Window
      .partitionBy("DISTRICT")
      .orderBy(desc("CRIME_TYPE_COUNT_PER_DISTRICT"))

    val crimeTypesCountPerDistrictRankedDF = crimeTypesCountPerDistrictDF
      .select(
        crimeTypesCountPerDistrictDF("DISTRICT"),
        regexp_replace(crimeTypesCountPerDistrictDF("NAME"), "\\s*-.*","") as("CRIME_TYPE"),
        crimeTypesCountPerDistrictDF("CRIME_TYPE_COUNT_PER_DISTRICT")
      )
      .withColumn("CRIME_TYPE_RANK", rank().over(crimeTypesRankPerDistrictWindow))

    val mostFrequentCrimeTypesPerDistrictDF = crimeTypesCountPerDistrictRankedDF
      .where(crimeTypesCountPerDistrictRankedDF("CRIME_TYPE_RANK") < 4)
  }
}

