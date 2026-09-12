"""TPOT AutoML wrapper — feature engineering + model search.

Thin adapter so the rest of the platform never imports TPOT directly; keeps the
heavy/optional dependency isolated to the batch plane.
"""
from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any


@dataclass
class AutoMLConfig:
    generations: int = 5
    population_size: int = 50
    max_time_mins: int = 60
    scoring: str = "f1_weighted"
    random_state: int = 42  # seed everything — stochastic search must be reproducible


@dataclass
class AutoMLOutcome:
    best_pipeline_repr: str
    cv_score: float
    exported_artifact_uri: str
    params: dict[str, Any] = field(default_factory=dict)


def run_tpot(dataset_uri: str, config: AutoMLConfig, task: str = "classification") -> AutoMLOutcome:
    """Run a TPOT search and return the best pipeline's summary.

    TODO:
      1. Load the dataset from ``dataset_uri`` (S3) into X / y.
      2. Construct ``TPOTClassifier`` or ``TPOTRegressor`` from ``config``.
      3. ``fit`` on a train split; score on a held-out eval set.
      4. ``export`` the winning sklearn pipeline + log it to MLflow.
    """
    raise NotImplementedError  # pragma: no cover - stub
