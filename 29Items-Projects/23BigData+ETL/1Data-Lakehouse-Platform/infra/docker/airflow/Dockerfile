# Airflow image for self-managed deployments and integration tests.
# In MWAA this image is NOT used — only requirements.txt + dags are synced.
FROM apache/airflow:2.9.3-python3.11

COPY orchestration/airflow/requirements.txt /requirements.txt
RUN pip install --no-cache-dir -r /requirements.txt

# dbt project rides along so BashOperator can run `dbt build`
COPY transform/dbt /opt/airflow/dbt
COPY orchestration/airflow/dags /opt/airflow/dags

# Build from the repo root:  docker build -f infra/docker/airflow/Dockerfile .
