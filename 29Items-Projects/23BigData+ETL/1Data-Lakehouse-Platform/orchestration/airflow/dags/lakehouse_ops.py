"""Shared helpers for lakehouse DAGs (not a DAG file — see .airflowignore).

Spark submission is environment-driven: when LAKEHOUSE_EMR_APPLICATION_ID is
set (MWAA in AWS), jobs go to EMR Serverless; otherwise they run via the local
runner — the same job modules either way.
"""

from __future__ import annotations

import os

from airflow.datasets import Dataset
from airflow.models.baseoperator import BaseOperator
from airflow.operators.bash import BashOperator

GOLD_ORDERS_DATASET = Dataset("delta://gold/sales/order_daily_stats")

EMR_APPLICATION_ID = os.environ.get("LAKEHOUSE_EMR_APPLICATION_ID", "")
EMR_EXECUTION_ROLE_ARN = os.environ.get("LAKEHOUSE_EMR_JOB_ROLE_ARN", "")
ARTIFACTS_BUCKET = os.environ.get("LAKEHOUSE_ARTIFACTS_BUCKET", "lakehouse-artifacts")
WHEEL_VERSION = os.environ.get("LAKEHOUSE_WHEEL_VERSION", "latest")
DBT_DIR = os.environ.get("LAKEHOUSE_DBT_DIR", "/opt/airflow/dbt")


def spark_task(task_id: str, module: str, arguments: list[str], **kwargs) -> BaseOperator:
    """One definition of 'run a lakehouse job' for both deployment targets."""
    if EMR_APPLICATION_ID:
        from airflow.providers.amazon.aws.operators.emr import EmrServerlessStartJobRunOperator

        return EmrServerlessStartJobRunOperator(
            task_id=task_id,
            application_id=EMR_APPLICATION_ID,
            execution_role_arn=EMR_EXECUTION_ROLE_ARN,
            job_driver={
                "sparkSubmit": {
                    "entryPoint": f"s3://{ARTIFACTS_BUCKET}/entrypoints/run_module.py",
                    "entryPointArguments": [module, *arguments],
                    "sparkSubmitParameters": (
                        f"--py-files s3://{ARTIFACTS_BUCKET}/wheels/{WHEEL_VERSION}/lakehouse.whl"
                    ),
                }
            },
            **kwargs,
        )
    command = " ".join(["python", "-m", module, *arguments])
    return BashOperator(task_id=task_id, bash_command=command, **kwargs)
