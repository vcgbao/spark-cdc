ThisBuild / version := "0.1.0"

ThisBuild / scalaVersion := "2.13.14"

lazy val root = (project in file("."))
  .settings(
    name := "spark-cdc"
  )

val sparkVersion = "3.5.2"

resolvers += "Confluent" at "https://packages.confluent.io/maven"

libraryDependencies ++= Seq(
  "org.apache.spark" % "spark-sql_2.13" % sparkVersion,
  "org.apache.spark" % "spark-sql-kafka-0-10_2.13" % sparkVersion,
  "org.apache.spark" % "spark-avro_2.13" % sparkVersion,
  "org.apache.iceberg" % "iceberg-spark-runtime-3.5_2.13" % "1.6.1",
  "za.co.absa" % "abris_2.13" % "6.4.1",
  "org.postgresql" % "postgresql" % "42.7.4"
)

