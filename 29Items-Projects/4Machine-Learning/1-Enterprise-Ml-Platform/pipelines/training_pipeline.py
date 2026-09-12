"""Kubeflow training pipeline: data-prep -> train -> evaluate -> register.

Defined with the KFP v2 DSL. Components are thin wrappers around platform
services so their logic stays unit-testable as plain Python.
"""
from __future__ import annotations

from kfp import dsl


@dsl.component(base_image="python:3.12-slim", packages_to_install=["pandas", "boto3"])
def prepare_data(dataset_uri: str, prepared_uri: str) -> str:
    """Validate, clean, and split the dataset; write a prepared artifact.

    TODO: load from S3, run schema/quality checks, train/val/test split.
    """
    return prepared_uri


@dsl.component(
    base_image="python:3.12-slim",
    packages_to_install=["scikit-learn", "xgboost", "mlflow"],
)
def train_model(prepared_uri: str, framework: str, run_name: str) -> str:
    """Train a model and log params/metrics/artifact to MLflow; return run_id.

    TODO: dispatch to the matching Trainer adapter; for deep-learning frameworks
    delegate to a SageMaker training job instead of in-component compute.
    """
    return "mlflow-run-id"


@dsl.component(base_image="python:3.12-slim", packages_to_install=["mlflow"])
def evaluate_model(run_id: str, min_score: float) -> bool:
    """Gate: True only if the run beats the promotion threshold.

    TODO: read eval metrics from the MLflow run and compare to ``min_score``.
    """
    return True


@dsl.component(base_image="python:3.12-slim", packages_to_install=["mlflow"])
def register_model(run_id: str, model_name: str) -> None:
    """Register the run's model and transition it to Staging.

    TODO: mlflow.register_model + transition_model_version_stage("Staging").
    """


@dsl.pipeline(
    name="enterprise-ml-training",
    description="End-to-end training & registration for the ML platform.",
)
def training_pipeline(
    dataset_uri: str,
    model_name: str,
    framework: str = "xgboost",
    min_score: float = 0.80,
) -> None:
    prep = prepare_data(dataset_uri=dataset_uri, prepared_uri=f"{dataset_uri}-prepared")
    train = train_model(
        prepared_uri=prep.output, framework=framework, run_name=model_name
    )
    gate = evaluate_model(run_id=train.output, min_score=min_score)
    with dsl.If(gate.output == True, name="passed-eval-gate"):  # noqa: E712
        register_model(run_id=train.output, model_name=model_name)


if __name__ == "__main__":
    from kfp import compiler

    compiler.Compiler().compile(training_pipeline, "training_pipeline.yaml")
