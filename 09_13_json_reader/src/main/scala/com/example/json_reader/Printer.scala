package com.example.json_reader

class Printer {

  def print(wineBottle: ABottleOfWine): Unit =
  {
    printf("========Bottle=========\nID : %d\nCOUNTRY : %s\nPOINTS : %d\nPRICE : %.2f\nTITLE : %s\nVARIETY : %s\nWINERY : %s\n",
      wineBottle.id,
      wineBottle.country,
      wineBottle.points,
      wineBottle.price,
      wineBottle.title,
      wineBottle.variety,
      wineBottle.winery
    )
  }
}
