#!/bin/bash
mkdir -p confluent-libs

libs=(
  "https://repo1.maven.org/maven2/org/apache/avro/avro/1.12.0/avro-1.12.0.jar"
  "https://repo1.maven.org/maven2/com/google/guava/failureaccess/1.0.1/failureaccess-1.0.1.jar"
  "https://repo1.maven.org/maven2/com/google/guava/guava/32.0.1-jre/guava-32.0.1-jre.jar"
  "https://packages.confluent.io/maven/io/confluent/common-config/7.7.0/common-config-7.7.0.jar"
  "https://packages.confluent.io/maven/io/confluent/common-utils/7.7.0/common-utils-7.7.0.jar"
  "https://packages.confluent.io/maven/io/confluent/kafka-avro-serializer/7.7.0/kafka-avro-serializer-7.7.0.jar"
  "https://packages.confluent.io/maven/io/confluent/kafka-connect-avro-converter/7.7.0/kafka-connect-avro-converter-7.7.0.jar"
  "https://packages.confluent.io/maven/io/confluent/kafka-connect-avro-data/7.7.0/kafka-connect-avro-data-7.7.0.jar"
  "https://packages.confluent.io/maven/io/confluent/kafka-schema-converter/7.7.0/kafka-schema-converter-7.7.0.jar"
  "https://packages.confluent.io/maven/io/confluent/kafka-schema-registry-client/7.7.0/kafka-schema-registry-client-7.7.0.jar"
  "https://packages.confluent.io/maven/io/confluent/kafka-schema-serializer/7.7.0/kafka-schema-serializer-7.7.0.jar"
)
cd confluent-libs
for i in ${!libs[@]}; do
  curl "${libs[$i]}" -O
done