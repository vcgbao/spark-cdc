package baovcg.spark.cdc
import org.apache.iceberg.spark.SparkSchemaUtil
import org.apache.spark.sql.{DataFrame, SparkSession}
import org.apache.spark.sql.functions.{col, expr}
import org.apache.spark.sql.streaming.Trigger
import za.co.absa.abris.config.AbrisConfig
import za.co.absa.abris.avro.functions.from_avro

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import scala.jdk.CollectionConverters._

object CdcToIceberg {
  private val userDir = System.getProperty("user.dir")
  private var env = "dev"
  private var topic = "inventory.inventory.customers"
  private var schemaRegistry = "http://localhost:8081"
  private var schemaVersion = 1
  private var kafkaBootstrapServers = "localhost:19092"
  private var destTable = "iceberg.inventory.customers"
  private var primaryKeys = "id".split(",")
  private val destDatabase = destTable.split("\\.").dropRight(1).mkString(".")
  private var triggerTime = "30 seconds"
  private var maintainTableBatchCount = 100
  private var checkpointLocation = s"file://$userDir/checkpoint"

  def parseArgs(args: Array[String]): Unit = {
    for (arg <- args) {
      val argParts = arg.split("=")
      if (argParts.size == 2) {
        val value = argParts.apply(1)
        argParts.head match {
          case "--env" => env = value
          case "--topic" => topic = value
          case "--schemaRegistry" => schemaRegistry = value
          case "--schemaVersion" => schemaVersion = value.toInt
          case "--kafkaBootstrapServers" => kafkaBootstrapServers = value
          case "--destTable" => destTable = value
          case "--primaryKeys" => primaryKeys = value.split(",")
          case "--triggerTime" => triggerTime = value
          case "--maintainTableBatchCount" => maintainTableBatchCount = value.toInt
          case "--checkpointLocation" => checkpointLocation = value
          case _ => throw new Exception(s"Unknown argument ${argParts.head}")
        }
      } else {
        throw new Exception(s"Invalid argument ${arg}")
      }
    }
  }

  private def getSparkSession(): SparkSession = {
    var sparkBuilder = SparkSession
      .builder()
      .appName("Spark Cdc To Iceberg")

    if (env == "dev") {
      sparkBuilder = sparkBuilder
        .master("local[*]")
        .config("spark.sql.extensions", "org.apache.iceberg.spark.extensions.IcebergSparkSessionExtensions")
        .config("spark.sql.catalog.iceberg", "org.apache.iceberg.spark.SparkCatalog")
        .config("spark.sql.catalog.iceberg.type", "hadoop")
        .config("spark.sql.catalog.iceberg.warehouse", s"file://${userDir}/warehouse")
    }
    sparkBuilder.getOrCreate()
  }

  def main(args: Array[String]): Unit = {
    parseArgs(args)
    val spark = getSparkSession()

    val abrisConfig = AbrisConfig
      .fromConfluentAvro
      .downloadReaderSchemaByVersion(schemaVersion)
      .andTopicNameStrategy(topic)
      .usingSchemaRegistry(schemaRegistry)

    val df = spark
      .readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", kafkaBootstrapServers)
      .option("subscribe", topic)
      .option("startingOffsets", "earliest")
      .load()
      .filter("value is not null")
      .withColumn("value", from_avro(col("value"), abrisConfig))
      .withColumn("data", expr("case when value.op = 'd' then value.before else value.after end"))
      .selectExpr(
        "data.*",
        "(case when value.op = 'd' then true else false end) as _is_delete",
        "partition as _topic_partition",
        "offset as _topic_offset"
      )


    if (!spark.catalog.databaseExists(destDatabase)) {
      spark.sql(s"create database $destDatabase")
    }


    if (!spark.catalog.tableExists(destTable)) {
      val icebergTblSchema = SparkSchemaUtil.convert(df.schema)
      val createDDL =
        s"""
          |CREATE TABLE $destTable (
          | ${icebergTblSchema.columns().stream().map(field => s"${field.name()} ${field.`type`().typeId().name()}").iterator().asScala.mkString(",")}
          |) USING iceberg
          |""".stripMargin
      spark.sql(createDDL)
    }

    df.writeStream
      .trigger(Trigger.ProcessingTime(triggerTime))
      .option("checkpointLocation", checkpointLocation)
      .foreachBatch((batch: DataFrame, id: Long ) => {
        if (!batch.isEmpty) {
          batch.createOrReplaceTempView(s"batch_$id")
          val mergeSql = s"""
               |WITH batch_deduplicated AS (
               |  SELECT ${batch.columns.toSeq.map(c => s"t.$c").mkString(", ")} FROM (
               |    SELECT b.*,
               |    ROW_NUMBER() OVER (PARTITION BY ${primaryKeys.toSeq.mkString(", ")} ORDER BY _topic_offset DESC) as rn
               |    FROM batch_$id b
               |  ) t WHERE rn = 1
               |)
               |MERGE INTO $destTable t
               |USING (SELECT * FROM batch_deduplicated) s
               |ON ${primaryKeys.toSeq.map(k => s"t.$k = s.$k").mkString(" and ")}
               |WHEN MATCHED AND t._topic_partition = s._topic_partition AND s._topic_offset > t._topic_offset THEN UPDATE SET *
               |WHEN NOT MATCHED THEN INSERT *
               |""".stripMargin
          batch.sparkSession.sql(mergeSql)
        }
        if (id % maintainTableBatchCount == 0) {
          val olderThan = LocalDateTime.now().minusHours(6).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
          spark.sql(s"CALL iceberg.system.expire_snapshots('${destTable}', TIMESTAMP '${olderThan}')")
          spark.sql(s"CALL iceberg.system.remove_orphan_files(table => '${destTable}')")
          spark.sql(s"CALL iceberg.system.rewrite_data_files('${destTable}')")
          spark.sql(s"CALL iceberg.system.rewrite_manifests('${destTable}')")
        }
      }:Unit).start()
    spark.streams.awaitAnyTermination()
    spark.stop()
  }
}
