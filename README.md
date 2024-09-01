# Debezium, Confluent Schema Registry, Spark Structured Streaming, and Apache Iceberg: A CDC Solution

## Configuration Steps
### 1. Download Confluent Schema Registry client libraries for Debezium.
```shell
sh ./download-confluent-libs.sh
```
### 2. Start Debezium
```shell
    docker compose up
```
### 3. Create Debezium Connector
```shell
curl --location 'localhost:8083/connectors/' \
--header 'Accept: application/json' \
--header 'Content-Type: application/json' \
--data '{
    "name": "inventory",
    "config": {
        "connector.class": "io.debezium.connector.mysql.MySqlConnector",
        "tasks.max": "1",
        "database.hostname": "mysql",
        "database.port": "3306",
        "database.user": "debezium",
        "database.password": "dbz",
        "database.server.id": "184054",
        "topic.prefix": "inventory",
        "database.include.list": "inventory",
        "schema.history.internal.kafka.bootstrap.servers": "kafka:9092",
        "schema.history.internal.kafka.topic": "inventory-schema-history"
    }
}'
```

### 4. Build project
```shell
sbt package
```

### 5. Submit Spark application
```shell
spark-submit --class baovcg.spark.cdc.CdcToIceberg --conf spark.sql.extensions=org.apache.iceberg.spark.extensions.IcebergSparkSessionExtensions --conf spark.sql.catalog.iceberg=org.apache.iceberg.spark.SparkCatalog --conf spark.sql.catalog.iceberg.type=hadoop --conf spark.sql.catalog.iceberg.warehouse=${PWD}/warehouse --packages org.apache.spark:spark-sql-kafka-0-10_2.13:3.5.2,org.apache.spark:spark-avro_2.13:3.5.2,org.apache.iceberg:iceberg-spark-runtime-3.5_2.13:1.6.1,za.co.absa:abris_2.13:6.4.1 target/scala-2.13/spark-cdc_2.13-0.1.0.jar
```