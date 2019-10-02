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

    val crimesFilePath = args(0)
    val offenceCodesFilePath = args(1)
    val outputFilePath = args(2)

    val codesDF = spark
      .read
      .option("header", "true")
      .option("inferSchema", "true")
      .csv(offenceCodesFilePath)
      .select(
        col("CODE") as "crime_type_id",
        regexp_replace(col("NAME"), "\\s*-.*", "") as "crime_type_name"
      )

    val crimesDF = spark
      .read
      .option("header", "true")
      .option("inferSchema", "true")
      .csv(crimesFilePath)
      .select(
        (when(
          col("DISTRICT").isNull,  "UNKNOWN")
          ).otherwise(col("DISTRICT")
        ) as ("district_id"),
        col("INCIDENT_NUMBER")  as "incident_id",
        col("OFFENSE_CODE")     as "crime_type_id",
        col("lat")              as "latitude",
        col("long")             as "longitude",
        col("MONTH")            as "month_number"
      )

    /*
    *
    * Basic Stats
    *
    * */
    val crimesBasicStatsDF = crimesDF
      .groupBy("district_id")
      .agg(
        count("incident_id")  as "crimes_total",
        avg("latitude")       as "lat",
        avg("longitude")      as "lng"
      )
      .orderBy(desc("crimes_total"))
      .select(
        col("district_id"),
        col("crimes_total"),
        col("lat"),
        col("lng")
      )

    /*
    *
    * Most frequent crime types per district
    *
    * */

    val crimeTypesCountPerDistrictDF = crimesDF
      .join(broadcast(codesDF), ("crime_type_id"))
      .groupBy(crimesDF("district_id"), crimesDF("crime_type_id"), codesDF("crime_type_name"))
      .agg(count("incident_id") as "crime_type_count")

    val crimeTypesRankPerDistrictWindow = Window
      .partitionBy("district_id")
      .orderBy(desc("crime_type_count"))

    val crimeTypesCountPerDistrictRankedDF = crimeTypesCountPerDistrictDF
      .select(
        col("district_id"),
        col("crime_type_id"),
        col("crime_type_name"),
        col("crime_type_count")
      )
      .withColumn("crime_type_rank", rank().over(crimeTypesRankPerDistrictWindow))

    val threeMostFrequentCrimeTypesPerDistrictDF =
      crimeTypesCountPerDistrictRankedDF.as("first_most_frequent_crime_type")
      .where(col("first_most_frequent_crime_type.crime_type_rank") === 1)
        .join(
          crimeTypesCountPerDistrictRankedDF.as("second_most_frequent_crime_type")
            .where(col("second_most_frequent_crime_type.crime_type_rank") === 2),
          "district_id"
        )
        .join(
        crimeTypesCountPerDistrictRankedDF.as("third_most_frequent_crime_type")
          .where(col("third_most_frequent_crime_type.crime_type_rank") === 3),
        "district_id"
      )
        .withColumn("frequent_crime_types", concat_ws(
          ", ",
          col("first_most_frequent_crime_type.crime_type_name"),
          col("second_most_frequent_crime_type.crime_type_name"),
          col("third_most_frequent_crime_type.crime_type_name")
        ))
          .select(
            col("district_id"),
            col("frequent_crime_types")
          )

    /*
    *
    * Crimes count per month median
    *
    * */

    val incidentsPerMonthPerDistrictDF = crimesDF
      .groupBy(col("district_id"), col("month_number"))
      .agg(count("incident_id") as "incidents_count_per_month")

    val incidentsPerMonthRankWindow = Window
      .partitionBy("district_id")
      .orderBy(desc("incidents_count_per_month"))

    val crimesPerMonthPerDistrictStatsDF = incidentsPerMonthPerDistrictDF
      .select(
        col("district_id"),
        col("month_number"),
        col("incidents_count_per_month")
      )
      .withColumn("month_rank_per_district", rank().over(incidentsPerMonthRankWindow))

    val crimesPerMonthPerDistrictMediansDF =
      crimesPerMonthPerDistrictStatsDF.as("incidents_per_month_6th_rank")
      .where(col("incidents_per_month_6th_rank.month_rank_per_district") === 6)
      .join(
        crimesPerMonthPerDistrictStatsDF.as("incidents_per_month_7th_rank")
          .where(col("incidents_per_month_7th_rank.month_rank_per_district") === 7),
        ("district_id")
      )
      .select(
        col("district_id"),
        (col("incidents_per_month_6th_rank.incidents_count_per_month")
          + col("incidents_per_month_7th_rank.incidents_count_per_month"))/2 as "crimes_monthly"
      ).distinct()

    /*
    *
    * Result
    *
    */

    val bostonCrimesMapDF = crimesBasicStatsDF
      .join(
        crimesPerMonthPerDistrictMediansDF,
        "district_id"
      )
      .join(
        threeMostFrequentCrimeTypesPerDistrictDF,
        "district_id"
      ).orderBy(desc("crimes_total"))

      bostonCrimesMapDF.write.parquet(outputFilePath + "/parquet/")
   }
}

