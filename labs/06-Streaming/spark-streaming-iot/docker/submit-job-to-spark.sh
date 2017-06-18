#!/usr/bin/env bash

#
# Submits streaming Spark application to the Spark cluster
#

docker-compose exec master spark-submit \
    --master spark://master:7077 \
    --class NAME_OF_CLASSS_WITH_MAIN \
    /app/PATH_TO_JAR_FILE_INSIDE_SPARK_DIR \
    kafka:9092 TOPIC_NAME OTHER_PARAMETERS_USED_IN_MAIN
