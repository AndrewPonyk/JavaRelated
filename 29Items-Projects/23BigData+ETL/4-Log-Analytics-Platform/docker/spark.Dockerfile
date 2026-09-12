# Spark job image — runs the streaming jobs in containers (the supported way on Windows
# dev boxes, see TECH-NOTES #11) and an EMR-on-EKS option. EMR Serverless consumes plain
# code artifacts instead (deploy.yml zips src/ to S3), so this image is NOT on the prod
# critical path.

FROM apache/spark:3.5.1-scala2.12-java17-python3-ubuntu

USER root
WORKDIR /opt/app

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1

# Executors need the sink/ML deps (httpx, sklearn, pandas…); pyspark comes from the base.
COPY requirements.txt ./
RUN pip3 install -r requirements.txt
# mapInPandas (anomaly_scoring) serializes via Arrow — without pyarrow the job dies at
# start ("PyArrow >= 4.0.0 must be installed"). Own layer so the big layer above stays
# cached. Keep the range in sync with requirements-spark.txt.
RUN pip3 install "pyarrow>=15,<21"

COPY src/ src/
COPY config/ config/

ENV PYTHONPATH=/opt/app/src \
    HOME=/opt/app
# Ivy resolves the Kafka connector at submit time into $HOME/.ivy2 (cached via volume).
RUN mkdir -p /opt/app/.ivy2 /checkpoints && chown -R spark:spark /opt/app /checkpoints

USER spark

# The Kafka connector version MUST match the Spark version (TECH-NOTES pitfall #4).
ENV SPARK_KAFKA_PACKAGE=org.apache.spark:spark-sql-kafka-0-10_2.12:3.5.1

# Usage (docker compose --profile pipeline up runs these for you):
#   /opt/spark/bin/spark-submit --packages $SPARK_KAFKA_PACKAGE \
#     /opt/app/src/log_analytics/streaming/jobs/<job>.py
# spark.jars.ivy: Java resolves user.home to /home/spark regardless of $HOME — point
# ivy at the writable, volume-cached location explicitly.
ENTRYPOINT ["/bin/bash", "-c", "exec /opt/spark/bin/spark-submit --master local[2] --packages ${SPARK_KAFKA_PACKAGE} --conf spark.jars.ivy=/opt/app/.ivy2 --properties-file /opt/app/config/spark/spark-defaults.conf /opt/app/src/log_analytics/streaming/jobs/${SPARK_JOB:-enrich_and_index}.py"]
