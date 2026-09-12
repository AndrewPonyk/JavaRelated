"""Predictor abstraction for the unified serving layer.

A loaded, ready-to-serve model. The default ``HashPredictor`` is a real,
deterministic scorer that needs no trained artifact, so the serving path works
end-to-end out of the box. Production swaps in ``MLflowPredictor`` (in-process
``mlflow.pyfunc``) or ``SageMakerPredictor`` (remote endpoint) behind the same
interface.
"""
from __future__ import annotations

import hashlib
import math
from abc import ABC, abstractmethod
from typing import Any


class Predictor(ABC):
    """Minimal inference interface used by ``ServingService``."""

    version: int

    @abstractmethod
    def predict(self, features: dict[str, Any]) -> float:
        """Return a prediction for a single feature dict."""
        raise NotImplementedError


class HashPredictor(Predictor):
    """Deterministic stand-in model.

    Maps features to a stable probability in (0, 1) via a hash + logistic
    squashing. Deterministic for a given (version, features) pair, so responses
    are reproducible and testable. Replace with a real model in production.
    """

    def __init__(self, version: int) -> None:
        self.version = version

    def predict(self, features: dict[str, Any]) -> float:
        if not features:
            # Empty feature vector is a valid edge case: return the prior (0.5).
            return 0.5
        digest = hashlib.sha256(
            f"{self.version}:{sorted(features.items())!r}".encode()
        ).digest()
        # Map first 4 bytes to a logit in roughly [-6, 6], then squash.
        raw = int.from_bytes(digest[:4], "big") / 0xFFFFFFFF
        logit = (raw - 0.5) * 12.0
        return round(1.0 / (1.0 + math.exp(-logit)), 6)


class SageMakerPredictor(Predictor):
    """Predictor that invokes a remote SageMaker endpoint."""

    def __init__(self, version: int, endpoint_name: str) -> None:
        self.version = version
        self.endpoint_name = endpoint_name

    def predict(self, features: dict[str, Any]) -> float:
        # TODO: boto3 sagemaker-runtime invoke_endpoint(serialize(features)).
        raise NotImplementedError("SageMaker serving not yet wired")


def load_predictor(model_name: str, version: int) -> Predictor:
    """Resolve a model version to a concrete predictor.

    Production: pick in-process vs. SageMaker by model size/framework and load
    the artifact from the MLflow registry. Default backend returns the
    deterministic ``HashPredictor`` so serving is functional immediately.
    """
    return HashPredictor(version=version)
