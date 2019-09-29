# Домашнее задание

## 20190913 : Введение в Scala

### Цель : Написать распределенное приложение для чтения JSON-файлов

### Создание проекта

1. Для начала выполнения задания необходимы следующие инструменты:
    - sbt
    - IntelliJ IDEA

2.  Для создания проекта введите в консоли
 
    ```
    sbt new holdenk/sparkProjectTemplate.g8
    ```

3. В настройках можете укажите

    - имя проекта 
    ```
    name - json_reader_{ваша фамилия}
    ```
    - остальные параметры оставьте по-умолчанию (просто нажмайте Enter)

4. Импортируйте проект в IntelliJ IDEA

File > New > Project from existing sources

### Задание

Напишите приложение, которое читает [json-файл](https://storage.googleapis.com/otus_sample_data/winemag-data.json.tgz) с помощью **Spark RDD API**, конвертирует его содержимое в case class’ы и распечатывает их в stdout.
Расположение файла передается первым и единственным аргументом.

### Сборка и запуск приложения

1. Главный класс приложения должен называться JsonReader
2. Собрать приложение можно с помощью команды ```sbt assembly```
3. Для запуска приложения через спарк нужно скачать Spark (версия 2.3.0, scala 2.11)
4. Приложение запускается командой
    ```bash
    /path/to/spark/bin/spark-submit --master local[*] --class com.example.JsonReader /path/to/assembly-jar {path/to/winemag.json}
    ```

### Подсказки

1. Для чтения файла можно использовать
    ```scala
    spark.sparkContext.textFile("README.md")
    ```
2. Для десериализации JSON можно использовать библиотеку json4s
    - [статья с примерами](https://eax.me/scala-json/)
    - [сама библиотека](https://github.com/json4s/json4s)    
    ```scala
    val decodedUser = parse(json).extract[User]
    // где User - это ваш case class
    ```