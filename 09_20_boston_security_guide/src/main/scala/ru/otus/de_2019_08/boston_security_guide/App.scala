package ru.otus.de_2019_08.boston_security_guide

import org.apache.spark.sql.SparkSession

object App {
  def main(args : Array[String]): Unit = {

    val spark = SparkSession.builder()
      .appName("Boston Security Guide")
      .master("local[*]")
      .getOrCreate()

    val resourcePath = getClass
      .getResource("/data_files/crimes_in_boston")
      .getPath

    val offenceCodesFilePath = resourcePath + "/offense_codes.csv"

    val crimesFilePath = resourcePath + "/crime.csv"

    val codes = spark
      .read
      .option("header","true")
      .option("inferSchema","true")
      .csv(offenceCodesFilePath)

    val crimes = spark
      .read
      .option("header","true")
      .option("inferSchema","true")
      .csv(crimesFilePath)

    /**
      *
      ```sql

      ```
      *
      *
     */


      codes.show(5)

//    val result = crimes
//         .join(
//           broadcast(codes),
//           crimes.col("OFFENSE_CODE") === codes.col("CODE"),
//           "left"
//         )
//         .groupBy("DISTRICT")
//         .agg(
//              count("INCIDENT_NUMBER").as("CRIMES_TOTAL_BY_DISTRICT"),
//              avg(crimes("Lat")).as("AVG_LATITUDE"),
//              avg(crimes("Long")).as("AVG_LONGITUDE")
//              )
//         .orderBy(desc("CRIMES_TOTAL"))


//      .explain()
//
//    val districts =  crimes
//      .select("DISTRICT")
//      .distinct()
//      .orderBy("DISTRICT")
//

//    crimes.createOrReplaceTempView("crimes_view")

//    val result = spark
//      .sql("SELECT " +
//        "COUNT(*)                         AS CRIMES_PER_DISTRICT_TOTAL, " +
//        "IFNULL(`DISTRICT`, 'UNKNOWN')    AS DISTRICT, " +
//        "avg(`Lat`)                       AS AVG_LATITUDE, " +
//        "avg(`Long`)                      AS AVG_LONGITUDE " +
//        "FROM crimes_view GROUP BY `DISTRICT`")
//
//    result.show(20)
  }
}

