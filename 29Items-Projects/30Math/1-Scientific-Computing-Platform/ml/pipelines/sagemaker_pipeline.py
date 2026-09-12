"""SageMaker Pipeline definition: train → register (PendingManualApproval).

Invoked by .github/workflows/ml-pipeline.yml (weekly + manual). Endpoint
promotion is a separate, human-approved step — the pipeline only registers
candidates in the Model Registry.

    python ml/pipelines/sagemaker_pipeline.py --upsert [--execute]

Required environment:
    SAGEMAKER_EXECUTION_ROLE_ARN   execution role assumed by SageMaker jobs
    ML_ARTIFACTS_BUCKET            S3 bucket for datasets and model artifacts
"""

from __future__ import annotations

import argparse
import os

PIPELINE_NAME = "scp-equation-recognizer"
MODEL_PACKAGE_GROUP = "scp-equation-recognizer"
FRAMEWORK_VERSION = "1.2-1"  # sklearn serving/training container version
INSTANCE_TYPE = "ml.m5.large"


def build_pipeline(role_arn: str, bucket: str):
    """Assemble the pipeline object. Imports are local: the sagemaker SDK is
    heavy and only needed when actually defining/executing the pipeline."""
    from sagemaker.sklearn.estimator import SKLearn
    from sagemaker.workflow.parameters import ParameterFloat, ParameterInteger
    from sagemaker.workflow.pipeline import Pipeline
    from sagemaker.workflow.step_collections import RegisterModel
    from sagemaker.workflow.steps import TrainingStep

    c_param = ParameterFloat(name="LogRegC", default_value=1.0)
    n_per_label = ParameterInteger(name="NPerLabel", default_value=300)

    estimator = SKLearn(
        entry_point="train.py",
        source_dir="ml/training",
        dependencies=["libs/sciengine/src/sciengine"],  # featurization parity in-container
        framework_version=FRAMEWORK_VERSION,
        instance_type=INSTANCE_TYPE,
        instance_count=1,
        role=role_arn,
        output_path=f"s3://{bucket}/models/equation-recognizer/",
        hyperparameters={"c": c_param, "n-per-label": n_per_label},
        metric_definitions=[
            {
                "Name": "validation:accuracy",
                "Regex": r'\{"metric": "validation:accuracy", "value": ([0-9.]+)\}',
            }
        ],
    )

    train_step = TrainingStep(
        name="TrainEquationRecognizer",
        estimator=estimator,
        inputs={"train": f"s3://{bucket}/datasets/equations/train/"},
    )

    register_step = RegisterModel(
        name="RegisterEquationRecognizer",
        estimator=estimator,
        model_data=train_step.properties.ModelArtifacts.S3ModelArtifacts,
        content_types=["application/json"],
        response_types=["application/json"],
        inference_instances=[INSTANCE_TYPE],
        transform_instances=[INSTANCE_TYPE],
        model_package_group_name=MODEL_PACKAGE_GROUP,
        approval_status="PendingManualApproval",
    )

    return Pipeline(
        name=PIPELINE_NAME,
        parameters=[c_param, n_per_label],
        steps=[train_step, register_step],
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--upsert", action="store_true", help="create/update the pipeline")
    parser.add_argument("--execute", action="store_true", help="start an execution")
    args = parser.parse_args()

    role_arn = os.environ["SAGEMAKER_EXECUTION_ROLE_ARN"]  # from CI (OIDC-assumed)
    bucket = os.environ["ML_ARTIFACTS_BUCKET"]

    pipeline = build_pipeline(role_arn, bucket)
    if args.upsert:
        pipeline.upsert(role_arn=role_arn)
        print(f"Pipeline '{PIPELINE_NAME}' upserted.")
    if args.execute:
        execution = pipeline.start()
        print(f"Execution started: {execution.arn}")
    if not (args.upsert or args.execute):
        parser.error("Nothing to do: pass --upsert and/or --execute")


if __name__ == "__main__":
    main()
