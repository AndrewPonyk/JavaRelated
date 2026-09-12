# Spark runner — Linux-parity environment for the Spark test lane and the
# end-to-end smoke pipeline. EMR Serverless does NOT use this image (its
# release label provides Spark); this exists because local Spark workers are
# unreliable on some Windows workstations and CI needs the same environment.
#
# Build from the REPO ROOT:
#   docker build -f infra/docker/spark/Dockerfile -t lakehouse-spark .
# If infra/docker/spark/wheels/ holds a pyspark wheel it is used (fast local
# builds); otherwise pip falls back to PyPI.
FROM python:3.11-slim

RUN apt-get update \
    && apt-get install -y --no-install-recommends default-jre-headless procps \
    && rm -rf /var/lib/apt/lists/*

COPY infra/docker/spark/wheels /tmp/wheels
# chmod: wheels built on Windows lose the executable bit on pyspark's launch scripts
RUN pip install --no-cache-dir --find-links /tmp/wheels \
    "pyspark>=3.5.4,<3.6" \
    "delta-spark==3.2.0" \
    "kafka-python>=2.2" \
    "pytest>=8.0" \
    && rm -rf /tmp/wheels \
    && chmod +x /usr/local/lib/python3.11/site-packages/pyspark/bin/* \
    && chmod +x /usr/local/lib/python3.11/site-packages/pyspark/sbin/*

# The lakehouse package is mounted (dev/tests) or copied (standalone runs).
COPY data-platform /opt/data-platform
RUN pip install --no-cache-dir --no-deps /opt/data-platform

ENV LAKEHOUSE_LOCAL=1 \
    PYSPARK_PYTHON=python \
    SPARK_LOCAL_IP=127.0.0.1

WORKDIR /work
CMD ["python", "-c", "import pyspark; print('lakehouse-spark ready, pyspark', pyspark.__version__)"]
