"""Train the final model and persist a versioned artifact.

The artifact bundles the fitted estimator, the fitted preprocessor, and metadata
(library versions, metrics, training timestamp, feature schema) so the serving
path can validate compatibility on load and reproduce transforms exactly.
"""

from __future__ import annotations

import logging
import platform
from dataclasses import asdict, dataclass
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

import joblib
import pandas as pd
from sklearn.metrics import accuracy_score, average_precision_score, roc_auc_score

from churn_predictor import ChurnError
from churn_predictor.config import settings
from churn_predictor.data import preprocessing
from churn_predictor.data.preprocessing import Preprocessor
from churn_predictor.models.tuning import _build_estimator, tune

logger = logging.getLogger(__name__)

ARTIFACT_VERSION = 2


@dataclass
class ArtifactMetadata:
    """Travels inside the persisted artifact for compatibility checks + audit."""

    artifact_version: int
    algorithm: str
    feature_columns: list[str]
    metrics: dict[str, float]
    params: dict[str, Any]
    trained_at: str
    python_version: str
    n_train: int
    n_test: int
    positive_rate: float


def train_model(
    df: pd.DataFrame,
    *,
    algorithms: tuple[str, ...] = ("xgboost", "lightgbm", "catboost"),
    artifact_path: str | None = None,
    n_trials: int | None = None,
    log_run: bool = False,
) -> ArtifactMetadata:
    """Full training run: split → fit preprocessor → tune → refit → evaluate → persist.

    Parameters
    ----------
    log_run:
        When True, also write a row to ``model_runs`` (requires a reachable DB).
        Failures to log are warned, not raised — training never fails on DB issues.
    """
    # 1. Split raw data (no leakage: the preprocessor is fit on the train split only).
    data = preprocessing.split(df)

    preprocessor = Preprocessor().fit(data.X_train)
    x_train = preprocessor.transform(data.X_train)
    x_test = preprocessor.transform(data.X_test)

    # 2. Search hyperparameters across candidate algorithms.
    result = tune(x_train, data.y_train, algorithms=algorithms, n_trials=n_trials)

    # 3. Refit the winning estimator on the full training split.
    estimator = _build_estimator(result.algorithm, result.best_params)
    estimator.fit(x_train, data.y_train)

    # 4. Evaluate on the untouched holdout.
    proba = estimator.predict_proba(x_test)[:, 1]
    preds = (proba >= 0.5).astype(int)
    metrics = {
        "holdout_roc_auc": float(roc_auc_score(data.y_test, proba)),
        "holdout_pr_auc": float(average_precision_score(data.y_test, proba)),
        "holdout_accuracy": float(accuracy_score(data.y_test, preds)),
        "cv_roc_auc": float(result.best_score),
    }
    logger.info("Holdout metrics: %s", metrics)

    metadata = ArtifactMetadata(
        artifact_version=ARTIFACT_VERSION,
        algorithm=result.algorithm,
        feature_columns=list(preprocessor.feature_columns),
        metrics=metrics,
        params=result.best_params,
        trained_at=datetime.now(UTC).isoformat(),
        python_version=platform.python_version(),
        n_train=int(len(x_train)),
        n_test=int(len(x_test)),
        positive_rate=float(data.y_train.mean()),
    )

    # 5. Persist the bundle (model + preprocessor + metadata).
    path = Path(artifact_path or settings.model_artifact_path)
    path.parent.mkdir(parents=True, exist_ok=True)
    joblib.dump(
        {"model": estimator, "preprocessor": preprocessor, "metadata": asdict(metadata)},
        path,
    )
    logger.info("Saved artifact -> %s", path)

    # 6. Optionally log the run to PostgreSQL for lineage / model registry.
    if log_run:
        try:
            from churn_predictor.db.repository import save_model_run

            run_id = save_model_run(asdict(metadata))
            logger.info("Logged model run id=%d", run_id)
        except ChurnError as exc:  # pragma: no cover - depends on live DB
            logger.warning("Could not log model run to DB: %s", exc)
        except Exception as exc:  # noqa: BLE001 - never fail training on logging
            logger.warning("Unexpected error logging model run: %s", exc)

    return metadata


if __name__ == "__main__":  # pragma: no cover - manual/CI entrypoint
    from churn_predictor.config import configure_logging
    from churn_predictor.data.loader import load_customers

    configure_logging()
    frame = load_customers(source="postgres")
    train_model(frame, log_run=True)
