"""Common training abstraction across PyTorch / TensorFlow / sklearn / XGBoost.

A single ``Trainer`` protocol lets the platform treat heterogeneous frameworks
uniformly; concrete adapters live alongside and are selected by framework name.
"""
from __future__ import annotations

from abc import ABC, abstractmethod
from dataclasses import dataclass
from typing import Any


@dataclass
class TrainingResult:
    """Outcome of a training run, ready to log to MLflow."""

    metrics: dict[str, float]
    model_artifact_uri: str
    params: dict[str, Any]


class Trainer(ABC):
    """Framework-agnostic training interface."""

    @abstractmethod
    def train(self, dataset_uri: str, hyperparameters: dict[str, Any]) -> TrainingResult:
        """Train a model and return its metrics + artifact location."""
        raise NotImplementedError


class SklearnTrainer(Trainer):
    """scikit-learn / XGBoost trainer adapter."""

    def train(self, dataset_uri: str, hyperparameters: dict[str, Any]) -> TrainingResult:
        # TODO: load data, fit estimator, evaluate, persist with mlflow.sklearn.
        raise NotImplementedError


class TorchTrainer(Trainer):
    """PyTorch trainer adapter."""

    def train(self, dataset_uri: str, hyperparameters: dict[str, Any]) -> TrainingResult:
        # TODO: build DataLoader, training loop, mlflow.pytorch.log_model.
        raise NotImplementedError


class TensorFlowTrainer(Trainer):
    """TensorFlow/Keras trainer adapter."""

    def train(self, dataset_uri: str, hyperparameters: dict[str, Any]) -> TrainingResult:
        # TODO: tf.data pipeline, model.fit, mlflow.tensorflow.log_model.
        raise NotImplementedError


def get_trainer(framework: str) -> Trainer:
    """Factory mapping a framework name to its trainer adapter."""
    registry: dict[str, type[Trainer]] = {
        "sklearn": SklearnTrainer,
        "xgboost": SklearnTrainer,
        "pytorch": TorchTrainer,
        "tensorflow": TensorFlowTrainer,
    }
    try:
        return registry[framework]()
    except KeyError as exc:
        raise ValueError(f"unsupported framework: {framework}") from exc
