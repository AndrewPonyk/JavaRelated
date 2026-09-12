"""Training pipeline for the fraud detection model.

Runs fully offline: data comes from a CSV (``--data-path``) or the built-in
synthetic generator, the model is a sklearn Pipeline, quality gates are
enforced before registration, and artifacts are always written to the
versioned local model store (see :mod:`fraud_detection.ml.registry`).
MLflow logging/registration additionally happens when mlflow is installed.

Usage:
    python -m fraud_detection.ml.train --n-samples 20000 --seed 42
    python -m fraud_detection.ml.train --data-path data/transactions.csv
"""

from __future__ import annotations

import argparse
import json
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

import numpy as np
import pandas as pd
from sklearn.linear_model import LogisticRegression
from sklearn.model_selection import train_test_split
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler

from fraud_detection.core.config import get_settings
from fraud_detection.core.logging import get_logger
from fraud_detection.ml.evaluate import evaluate_model, passes_quality_gates
from fraud_detection.ml.features import FEATURE_COLUMNS, build_features, category_hash
from fraud_detection.ml.registry import LocalModelStore

logger = get_logger(__name__)

REQUIRED_COLUMNS = {"amount", "merchant_category", "timestamp", "is_fraud"}

MERCHANT_CATEGORIES = [
    "grocery",
    "electronics",
    "travel",
    "gambling",
    "jewelry",
    "restaurants",
    "fuel",
    "entertainment",
    "clothing",
    "pharmacy",
    "utilities",
    "digital_goods",
]


class QualityGateError(RuntimeError):
    """Raised when a trained candidate fails the promotion quality gates."""

    def __init__(self, reasons: list[str]) -> None:
        super().__init__("; ".join(reasons))
        self.reasons = reasons


def generate_synthetic_transactions(n_samples: int = 20000, seed: int = 42) -> pd.DataFrame:
    """Generate a deterministic synthetic transactions dataset.

    The ground-truth fraud probability is a logistic function of the same
    canonical features the model consumes, so the signal is learnable and
    the quality gates are meaningful. This generator is the documented demo
    data source; production training points ``--data-path`` (or
    ``FRAUD_TRAINING_DATA_PATH``) at a real extraction.

    Args:
        n_samples: Number of transactions.
        seed: RNG seed.

    Returns:
        Frame with columns ``amount``, ``merchant_category``, ``timestamp``
        and ``is_fraud`` (base fraud rate roughly 3-5%).
    """
    rng = np.random.default_rng(seed)
    amounts = np.round(rng.lognormal(mean=4.0, sigma=1.2, size=n_samples), 2)
    categories = rng.choice(MERCHANT_CATEGORIES, size=n_samples)
    hours = rng.integers(0, 24, size=n_samples)
    days = rng.integers(0, 90, size=n_samples)
    timestamps = (
        pd.Timestamp("2026-01-01", tz="UTC")
        + pd.to_timedelta(days, unit="D")
        + pd.to_timedelta(hours, unit="h")
    )

    category_risk = np.array([category_hash(str(c)) for c in categories])
    logit = -11.7 + 1.4 * np.log1p(amounts) + 3.2 * category_risk + 0.09 * hours
    probability = 1.0 / (1.0 + np.exp(-logit))
    labels = (rng.random(n_samples) < probability).astype(int)

    return pd.DataFrame(
        {
            "amount": amounts,
            "merchant_category": categories,
            "timestamp": timestamps,
            "is_fraud": labels,
        }
    )


def load_training_frame(
    data_path: str = "", n_samples: int = 20000, seed: int = 42
) -> tuple[pd.DataFrame, str]:
    """Load the training frame from CSV or the synthetic generator.

    Args:
        data_path: Optional CSV path; must contain ``REQUIRED_COLUMNS``.
        n_samples: Synthetic dataset size (ignored for CSV input).
        seed: Synthetic RNG seed (ignored for CSV input).

    Returns:
        Tuple of (frame, data source description).

    Raises:
        ValueError: If the CSV is missing required columns.
    """
    if data_path:
        frame = pd.read_csv(data_path)
        missing = REQUIRED_COLUMNS - set(frame.columns)
        if missing:
            raise ValueError(f"training CSV {data_path!r} is missing columns: {sorted(missing)}")
        frame["timestamp"] = pd.to_datetime(frame["timestamp"], utc=True)
        return frame, data_path
    return generate_synthetic_transactions(n_samples=n_samples, seed=seed), "synthetic"


def compute_baseline(x_train: pd.DataFrame, bins: int = 10) -> dict[str, Any]:
    """Compute per-feature PSI baseline histograms from the training split.

    Args:
        x_train: Training features (canonical columns).
        bins: Number of histogram bins.

    Returns:
        Mapping ``feature -> {mean, std, bin_edges, bin_counts}``.
    """
    baseline: dict[str, Any] = {}
    for column in FEATURE_COLUMNS:
        values = x_train[column].to_numpy(dtype=float)
        edges = np.histogram_bin_edges(values, bins=bins)
        counts, _ = np.histogram(values, bins=edges)
        baseline[column] = {
            "mean": float(values.mean()),
            "std": float(values.std()),
            "bin_edges": [float(edge) for edge in edges],
            "bin_counts": [int(count) for count in counts],
        }
    return baseline


def _log_to_mlflow(pipeline: Pipeline, metrics: dict[str, float]) -> None:
    """Log the run and register the model when mlflow is available.

    Policy: the local model store is the always-on registry; MLflow is an
    additive tier that is skipped (with a notice) when not installed.
    """
    try:
        import mlflow  # noqa: PLC0415 - deliberate lazy import
        import mlflow.sklearn  # noqa: PLC0415
        from mlflow.tracking import MlflowClient  # noqa: PLC0415
    except ImportError:
        logger.info("mlflow_unavailable_local_store_registration_only")
        return

    settings = get_settings()
    mlflow.set_tracking_uri(settings.mlflow_tracking_uri)
    with mlflow.start_run():
        mlflow.log_metrics(metrics)
        mlflow.log_params({"feature_columns": ",".join(FEATURE_COLUMNS)})
        mlflow.sklearn.log_model(
            pipeline, artifact_path="model", registered_model_name=settings.model_name
        )
    try:
        client = MlflowClient(tracking_uri=settings.mlflow_tracking_uri)
        latest = max(
            (
                int(mv.version)
                for mv in client.search_model_versions(f"name='{settings.model_name}'")
            ),
            default=None,
        )
        if latest is not None:
            client.set_registered_model_alias(settings.model_name, "challenger", str(latest))
    except Exception as exc:  # noqa: BLE001 - alias update is best-effort
        logger.warning("mlflow_challenger_alias_update_failed", error=str(exc))


def train_pipeline(
    model_dir: str | Path | None = None,
    n_samples: int = 20000,
    seed: int = 42,
    min_auc: float = 0.85,
    min_recall: float = 0.7,
    data_path: str = "",
    register: bool = True,
) -> dict[str, Any]:
    """Train, evaluate, gate and register a fraud model.

    Args:
        model_dir: Local model store directory (defaults to settings).
        n_samples: Synthetic dataset size.
        seed: RNG seed.
        min_auc: Quality gate: minimum ROC AUC on the holdout split.
        min_recall: Quality gate: minimum recall on the holdout split.
        data_path: Optional training CSV.
        register: When False, evaluate only (CI model-validation mode).

    Returns:
        Summary dict: ``version`` (None when not registered), ``metrics``,
        ``gates_passed``, ``data_source``, ``feature_names``,
        ``artifact_dir`` (None when not registered).

    Raises:
        QualityGateError: When the candidate fails a quality gate.
    """
    frame, data_source = load_training_frame(data_path, n_samples=n_samples, seed=seed)
    featured = build_features(frame)
    x = featured[FEATURE_COLUMNS]
    y = frame["is_fraud"].astype(int)

    x_train, x_test, y_train, y_test = train_test_split(
        x, y, test_size=0.25, random_state=seed, stratify=y
    )

    pipeline = Pipeline(
        [
            ("scaler", StandardScaler()),
            ("classifier", LogisticRegression(max_iter=1000, class_weight="balanced")),
        ]
    )
    pipeline.fit(x_train, y_train)
    y_prob = pipeline.predict_proba(x_test)[:, 1]

    metrics = evaluate_model(y_test.to_numpy(), y_prob)
    passed, reasons = passes_quality_gates(metrics, min_auc=min_auc, min_recall=min_recall)
    if not passed:
        raise QualityGateError(reasons)

    version: str | None = None
    artifact_dir: str | None = None
    if register:
        store = LocalModelStore(model_dir)
        metadata = {
            "metrics": metrics,
            "feature_names": FEATURE_COLUMNS,
            "trained_at": datetime.now(UTC).isoformat(),
            "data_source": data_source,
            "n_samples": int(len(frame)),
            "seed": seed,
        }
        version = store.save_version(pipeline, metadata, compute_baseline(x_train))
        artifact_dir = str(store.model_dir / f"v{version}")
        _log_to_mlflow(pipeline, metrics)

    return {
        "version": version,
        "metrics": metrics,
        "gates_passed": True,
        "data_source": data_source,
        "feature_names": FEATURE_COLUMNS,
        "artifact_dir": artifact_dir,
    }


def main(argv: list[str] | None = None) -> int:
    """Train, evaluate and register the fraud model; returns an exit code."""
    settings = get_settings()
    parser = argparse.ArgumentParser(description="Train the fraud detection model")
    parser.add_argument("--model-dir", default=settings.model_dir, help="Local model store dir")
    parser.add_argument(
        "--n-samples", type=int, default=settings.training_samples, help="Synthetic dataset size"
    )
    parser.add_argument("--seed", type=int, default=42, help="Random seed")
    parser.add_argument("--min-auc", type=float, default=0.85, help="Quality gate: minimum AUC")
    parser.add_argument(
        "--min-recall", type=float, default=0.7, help="Quality gate: minimum recall"
    )
    parser.add_argument(
        "--data-path",
        default=settings.training_data_path,
        help="Training CSV (empty = synthetic generator)",
    )
    parser.add_argument(
        "--no-register",
        action="store_true",
        help="Evaluate only; do not write to the model store or MLflow",
    )
    args = parser.parse_args(argv)

    try:
        summary = train_pipeline(
            model_dir=args.model_dir,
            n_samples=args.n_samples,
            seed=args.seed,
            min_auc=args.min_auc,
            min_recall=args.min_recall,
            data_path=args.data_path,
            register=not args.no_register,
        )
    except QualityGateError as exc:
        print(json.dumps({"gates_passed": False, "reasons": exc.reasons}, indent=2))
        return 1

    print(json.dumps(summary, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
