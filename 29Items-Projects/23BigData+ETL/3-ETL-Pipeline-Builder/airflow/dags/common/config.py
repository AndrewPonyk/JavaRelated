"""Environment-driven configuration shared by all DAGs.

Keep this module import-cheap: it is evaluated on every DAG parse (~30 s on MWAA).
No boto3 clients, no network calls at import time.
"""

from __future__ import annotations

import os
from dataclasses import dataclass


@dataclass(frozen=True)
class PipelineConfig:
    env: str = os.environ.get("APP_ENV", "dev")
    aws_region: str = os.environ.get("AWS_REGION", "eu-central-1")
    lake_bucket: str = os.environ.get("LAKE_BUCKET", "etl-pipeline-builder-dev-lake")
    artifacts_bucket: str = os.environ.get("ARTIFACTS_BUCKET", "etl-pipeline-builder-dev-artifacts")
    alerts_sns_topic_arn: str = os.environ.get("ALERTS_SNS_TOPIC_ARN", "")

    # Airflow connection ids (provisioned via the MWAA secrets backend / UI).
    snowflake_conn_id: str = "snowflake_default"
    aws_conn_id: str = "aws_default"

    # Paths inside the Airflow runtime (compose mounts / MWAA plugins layout).
    dbt_project_dir: str = os.environ.get("DBT_PROJECT_DIR", "/opt/project/dbt")
    dbt_profiles_dir: str = os.environ.get("DBT_PROFILES_DIR", "/opt/project/dbt/profiles")
    ge_root_dir: str = os.environ.get("GE_ROOT_DIR", "/opt/project/great_expectations")
    project_root: str = os.environ.get("PROJECT_ROOT", "/opt/project")

    # Cloud dbt execution (unset → local BashOperator path in the batch DAG).
    dbt_ecs_task_definition: str = os.environ.get("DBT_ECS_TASK_DEFINITION", "")
    ecs_cluster: str = os.environ.get("ECS_CLUSTER", "")
    private_subnet_ids: tuple[str, ...] = tuple(
        s for s in os.environ.get("PRIVATE_SUBNET_IDS", "").split(",") if s
    )


CFG = PipelineConfig()
