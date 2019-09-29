package ru.otus.de_2019_08.json_reader

class WineBottleFactory {

  private var __inputString:String = ""

  private var __id:Int = 0
  private var __country:String = ""
  private var __points:Int = 0
  private var __price:Float = 0
  private var __title:String = ""
  private var __variety:String = ""
  private var __winery:String = ""

  def create(args: String): ABottleOfWine = {

    this.__inputString = args

    args
      .replaceAll("[{}]","")
      .replaceAll(",\"","\t")
      .replaceAll("\"", "")
      .split("\t")
      .foreach(kvString => __setValues(kvString))

    return this.__newBottle()
  }

  private[this] def __setValues(keyValue:String): Unit =
  {

      val key = keyValue.split(":")(0)
      val value = keyValue.split(":")(1)

      key match {
        case "id" => this.__id = value.toInt
        case "country" => this.__country = value
        case "points" => this.__points = value.toInt
        case "price" => this.__price = value.toFloat
        case "title" => this.__title = value
        case "variety" => this.__variety = value
        case "winery" => this.__winery = value
      }
  }

  private[this] def __newBottle(): ABottleOfWine =
  {
    return ABottleOfWine(
      this.__id,
      this.__country,
      this.__points,
      this.__price,
      this.__title,
      this.__variety,
      this.__winery
    )
  }
}
