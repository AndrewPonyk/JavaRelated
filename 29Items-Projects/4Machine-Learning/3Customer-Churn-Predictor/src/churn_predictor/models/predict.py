"""Load the model artifact and score customers (single and batch).

The loaded artifact is cached so repeated Streamlit reruns don't re-read it from
disk. The Streamlit layer additionally wraps loading with ``st.cache_resource``.
"""

from __future__ import annotations

import logging
import urllib.request
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path
from typing import Any

import numpy as np
import pandas as pd

from churn_predictor import ModelArtifactError, PredictionError
from churn_predictor.config import settings

logger = logging.getLogger(__name__)

_REQUIRED_KEYS = ("model", "preprocessor", "metadata")


@dataclass
class Prediction:
    """A single scored result."""

    probability: float
    label: int
    risk_band: str  # low | medium | high


def risk_band(p: float) -> str:
    """Map a probability to a coarse risk band used across the UI."""
    if p >= 0.66:
        return "high"
    if p >= 0.33:
        return "medium"
    return "low"


def _ensure_artifact_present(path: Path) -> None:
    """Download the artifact from ``MODEL_REMOTE_URL`` if it is missing locally."""
    if path.exists():
        return
    remote = settings.model_remote_url
    if not remote:
        raise ModelArtifactError(f"Model artifact not found at {path}")
    logger.info("Artifact missing locally; downloading from %s", remote)
    path.parent.mkdir(parents=True, exist_ok=True)
    try:
        urllib.request.urlretrieve(remote, path)  # noqa: S310 - operator-configured URL
    except Exception as exc:  # noqa: BLE001
        raise ModelArtifactError(f"Failed to download artifact from {remote}: {exc}") from exc


@lru_cache(maxsize=4)
def load_artifact(path: str | None = None) -> dict[str, Any]:
    """Load and cache the persisted model bundle, validating compatibility."""
    import joblib  # local import keeps cold-start light

    p = Path(path or settings.model_artifact_path)
    _ensure_artifact_present(p)

    bundle = joblib.load(p)
    missing = [k for k in _REQUIRED_KEYS if k not in bundle]
    if missing:
        raise ModelArtifactError(f"Artifact is missing keys: {missing}")
    logger.info("Loaded model artifact: algorithm=%s", bundle["metadata"].get("algorithm"))
    return bundle


def predict_frame(df: pd.DataFrame, *, artifact_path: str | None = None) -> pd.DataFrame:
    """Score a DataFrame of raw customer rows; returns input + probability columns."""
    if df.empty:
        raise PredictionError("Cannot score an empty DataFrame")

    bundle = load_artifact(artifact_path)
    model = bundle["model"]
    preprocessor = bundle["preprocessor"]

    try:
        features = preprocessor.transform(df)
        proba = model.predict_proba(features)[:, 1]
    except Exception as exc:  # noqa: BLE001
        raise PredictionError(f"Scoring failed: {exc}") from exc

    out = df.copy()
    out["churn_probability"] = np.round(proba, 4)
    out["risk_band"] = [risk_band(float(p)) for p in proba]
    return out


def predict_one(record: dict[str, Any], *, artifact_path: str | None = None) -> Prediction:
    """Score a single customer record."""
    scored = predict_frame(pd.DataFrame([record]), artifact_path=artifact_path)
    p = float(scored["churn_probability"].iloc[0])
    return Prediction(probability=p, label=int(p >= 0.5), risk_band=risk_band(p))
