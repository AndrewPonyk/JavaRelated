"""SHAP-based explanations: global feature importance and per-prediction drivers.

Uses ``shap.TreeExplainer`` which is fast and exact for gradient-boosted trees.
All public functions accept **raw** customer frames and apply the bundled
preprocessor internally, so callers never need to know the model's feature schema.
"""

from __future__ import annotations

import logging
from dataclasses import dataclass
from functools import lru_cache
from typing import Any

import numpy as np
import pandas as pd

from churn_predictor.models.predict import load_artifact

logger = logging.getLogger(__name__)


@dataclass
class FeatureContribution:
    """A single feature's SHAP contribution for one prediction."""

    feature: str
    value: Any
    shap_value: float
    direction: str  # "increases" | "decreases" churn risk


@lru_cache(maxsize=4)
def _get_explainer(artifact_path: str | None = None):
    """Build and cache a TreeExplainer for the loaded model."""
    import shap  # local import; shap pulls heavy deps

    bundle = load_artifact(artifact_path)
    return shap.TreeExplainer(bundle["model"])


def _transform(raw: pd.DataFrame, artifact_path: str | None) -> pd.DataFrame:
    """Apply the bundled preprocessor to raw input."""
    return load_artifact(artifact_path)["preprocessor"].transform(raw)


def _positive_class_shap(shap_values: Any) -> np.ndarray:
    """Normalize SHAP output to a 2-D array of contributions for the positive class."""
    values = np.asarray(shap_values)
    if isinstance(shap_values, list):  # older API: list per class
        values = np.asarray(shap_values[-1])
    if values.ndim == 3:  # (n_samples, n_features, n_classes)
        values = values[:, :, -1]
    return values


def global_importance(
    raw: pd.DataFrame,
    *,
    max_samples: int = 1000,
    artifact_path: str | None = None,
) -> pd.DataFrame:
    """Mean absolute SHAP value per feature (global importance), sampled for speed."""
    X = _transform(raw, artifact_path)
    if len(X) > max_samples:
        X = X.sample(max_samples, random_state=42)

    explainer = _get_explainer(artifact_path)
    values = _positive_class_shap(explainer.shap_values(X))

    importance = np.abs(values).mean(axis=0)
    return (
        pd.DataFrame({"feature": X.columns, "mean_abs_shap": importance})
        .sort_values("mean_abs_shap", ascending=False)
        .reset_index(drop=True)
    )


def explain_one(
    raw_row: pd.DataFrame,
    *,
    top_n: int = 5,
    artifact_path: str | None = None,
) -> list[FeatureContribution]:
    """Return the top-N SHAP drivers for a single prediction (1-row raw frame)."""
    if len(raw_row) != 1:
        raise ValueError("explain_one expects exactly one row")

    X = _transform(raw_row, artifact_path)
    explainer = _get_explainer(artifact_path)
    contributions = _positive_class_shap(explainer.shap_values(X))[0]

    order = np.argsort(np.abs(contributions))[::-1][:top_n]
    out: list[FeatureContribution] = []
    for idx in order:
        sv = float(contributions[idx])
        out.append(
            FeatureContribution(
                feature=str(X.columns[idx]),
                value=X.iloc[0, idx],
                shap_value=sv,
                direction="increases" if sv > 0 else "decreases",
            )
        )
    return out
