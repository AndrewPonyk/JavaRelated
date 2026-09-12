# Custom Airflow image for local/self-hosted use.
# MWAA does NOT use this image — it consumes dags/ + requirements.txt synced to S3.
FROM apache/airflow:2.9.3-python3.11

COPY requirements.txt /requirements.txt
RUN pip install --no-cache-dir -r /requirements.txt

COPY dags/ /opt/airflow/dags/
