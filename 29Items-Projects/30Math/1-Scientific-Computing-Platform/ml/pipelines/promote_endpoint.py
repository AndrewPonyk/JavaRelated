"""Promote the latest APPROVED model package to the serverless endpoint.

Run by .github/workflows/ml-pipeline.yml behind the `ml-prod` environment
gate (human approval). Blue/green by construction: a new EndpointConfig is
created per promotion and UpdateEndpoint swaps traffic atomically.

Required environment:
    AWS_REGION                       target region
    ML_ENDPOINT_NAME                 e.g. scp-equation-recognizer
Optional:
    MODEL_PACKAGE_GROUP              defaults to scp-equation-recognizer
    SAGEMAKER_EXECUTION_ROLE_ARN     role for the created Model resource
    SERVERLESS_MEMORY_MB / SERVERLESS_MAX_CONCURRENCY
"""

from __future__ import annotations

import os
import time


def latest_approved_package(sm, group: str) -> str:
    response = sm.list_model_packages(
        ModelPackageGroupName=group,
        ModelApprovalStatus="Approved",
        SortBy="CreationTime",
        SortOrder="Descending",
        MaxResults=1,
    )
    packages = response.get("ModelPackageSummaryList", [])
    if not packages:
        raise SystemExit(f"No APPROVED model packages in group '{group}' — nothing to promote.")
    return packages[0]["ModelPackageArn"]


def main() -> None:
    import boto3

    region = os.environ["AWS_REGION"]
    endpoint_name = os.environ["ML_ENDPOINT_NAME"]
    group = os.environ.get("MODEL_PACKAGE_GROUP", "scp-equation-recognizer")
    role_arn = os.environ["SAGEMAKER_EXECUTION_ROLE_ARN"]
    memory_mb = int(os.environ.get("SERVERLESS_MEMORY_MB", "2048"))
    max_concurrency = int(os.environ.get("SERVERLESS_MAX_CONCURRENCY", "20"))

    sm = boto3.client("sagemaker", region_name=region)

    package_arn = latest_approved_package(sm, group)
    stamp = time.strftime("%Y%m%d-%H%M%S")
    model_name = f"{endpoint_name}-model-{stamp}"
    config_name = f"{endpoint_name}-config-{stamp}"

    sm.create_model(
        ModelName=model_name,
        ExecutionRoleArn=role_arn,
        PrimaryContainer={"ModelPackageName": package_arn},
    )
    sm.create_endpoint_config(
        EndpointConfigName=config_name,
        ProductionVariants=[
            {
                "VariantName": "AllTraffic",
                "ModelName": model_name,
                "ServerlessConfig": {
                    "MemorySizeInMB": memory_mb,
                    "MaxConcurrency": max_concurrency,
                },
            }
        ],
    )

    existing = sm.list_endpoints(NameContains=endpoint_name)["Endpoints"]
    if any(e["EndpointName"] == endpoint_name for e in existing):
        sm.update_endpoint(EndpointName=endpoint_name, EndpointConfigName=config_name)
        action = "updated"
    else:
        sm.create_endpoint(EndpointName=endpoint_name, EndpointConfigName=config_name)
        action = "created"

    print(f"Endpoint '{endpoint_name}' {action} from {package_arn} (config {config_name}).")
    print("Waiting for InService…")
    sm.get_waiter("endpoint_in_service").wait(EndpointName=endpoint_name)
    print("InService.")


if __name__ == "__main__":
    main()
