"""Feature drift detection via the Population Stability Index (PSI).

The baseline is the training-split histogram written next to every model
version (``baseline.json``); the current window is the feature vectors of
the most recent persisted predictions. When any monitored feature drifts
past the threshold, retraining is triggered automatically (subject to the
debounce policy in :mod:`fraud_detection.services.retraining`).
"""

from __future__ import annotations

from datetime import UTC, datetime
from typing import Any

import numpy as np
from sqlalchemy.orm import Session

from fraud_detection.core.config import get_settings
from fraud_detection.core.logging import get_logger
from fraud_detection.db.models import DriftReport
from fraud_detection.db.repositories import DriftReportRepository, PredictionRepository
from fraud_detection.ml.features import FEATURE_COLUMNS
from fraud_detection.ml.registry import LocalModelStore
from fraud_detection.monitoring.metrics import FRAUD_DB_ERRORS_TOTAL, FRAUD_DRIFT_PSI
from fraud_detection.schemas.model import DriftStatus, DriftSummary
from fraud_detection.services.retraining import RetrainingService

logger = get_logger(__name__)

_EPSILON = 1e-6


def population_stability_index(expected: np.ndarray, actual: np.ndarray, bins: int = 10) -> float:
    """Compute the Population Stability Index between two samples.

    Bin edges are derived from the ``expected`` (baseline) distribution and
    the outer edges are widened to +/- infinity so out-of-range ``actual``
    values still land in a bin. Zero-count bins are floored at a small
    epsilon to keep the logarithm finite.

    Args:
        expected: Baseline sample (e.g. training-time feature values).
        actual: Current sample (e.g. recent production feature values).
        bins: Number of histogram bins.

    Returns:
        PSI value; conventional reading: <0.1 stable, 0.1-0.2 moderate
        shift, >=0.2 significant drift.

    Raises:
        ValueError: If either sample is empty.
    """
    expected = np.asarray(expected, dtype=float).ravel()
    actual = np.asarray(actual, dtype=float).ravel()
    if expected.size == 0 or actual.size == 0:
        raise ValueError("PSI requires non-empty expected and actual samples")

    edges = np.histogram_bin_edges(expected, bins=bins)
    edges[0], edges[-1] = -np.inf, np.inf

    expected_counts, _ = np.histogram(expected, bins=edges)
    actual_counts, _ = np.histogram(actual, bins=edges)

    expected_frac = np.clip(expected_counts / expected_counts.sum(), _EPSILON, None)
    actual_frac = np.clip(actual_counts / actual_counts.sum(), _EPSILON, None)

    return float(np.sum((actual_frac - expected_frac) * np.log(actual_frac / expected_frac)))


def psi_from_baseline(baseline: dict[str, Any], actual: np.ndarray) -> float:
    """Compute PSI of ``actual`` against a stored baseline histogram.

    Args:
        baseline: One feature's entry from ``baseline.json``
            (``{mean, std, bin_edges, bin_counts}``).
        actual: Recent production values of the feature.

    Returns:
        PSI value.

    Raises:
        ValueError: If the actual sample is empty or the baseline invalid.
    """
    actual = np.asarray(actual, dtype=float).ravel()
    if actual.size == 0:
        raise ValueError("PSI requires a non-empty actual sample")
    edges = np.asarray(baseline["bin_edges"], dtype=float)
    expected_counts = np.asarray(baseline["bin_counts"], dtype=float)
    if edges.size != expected_counts.size + 1 or expected_counts.sum() <= 0:
        raise ValueError("invalid baseline histogram")
    edges = edges.copy()
    edges[0], edges[-1] = -np.inf, np.inf

    actual_counts, _ = np.histogram(actual, bins=edges)
    expected_frac = np.clip(expected_counts / expected_counts.sum(), _EPSILON, None)
    actual_frac = np.clip(actual_counts / actual_counts.sum(), _EPSILON, None)
    return float(np.sum((actual_frac - expected_frac) * np.log(actual_frac / expected_frac)))


class DriftDetector:
    """Evaluates recent production features against the champion baseline."""

    def __init__(self, retraining: RetrainingService | None = None) -> None:
        self._retraining = retraining

    def check(self, feature_name: str, baseline: np.ndarray, current: np.ndarray) -> DriftStatus:
        """Compare one feature's current sample against a baseline sample.

        Updates the ``fraud_drift_psi`` gauge as a side effect.
        """
        psi = population_stability_index(baseline, current)
        threshold = get_settings().drift_psi_threshold
        drift_detected = psi >= threshold
        FRAUD_DRIFT_PSI.labels(feature=feature_name).set(psi)
        return DriftStatus(
            feature_name=feature_name,
            psi_score=psi,
            threshold=threshold,
            drift_detected=drift_detected,
        )

    def evaluate(self, session: Session) -> DriftSummary:
        """Run the full drift evaluation over the recent prediction window.

        Loads the champion's training baseline, compares the recent
        persisted feature vectors per monitored feature, persists
        ``drift_reports`` rows, updates gauges, and auto-triggers
        retraining when drift is detected (debounced).

        Args:
            session: Database session.

        Returns:
            The aggregated :class:`DriftSummary`.
        """
        settings = get_settings()
        baseline = LocalModelStore(settings.model_dir).load_baseline("champion")
        if baseline is None:
            return DriftSummary(
                status="insufficient_data",
                detail="no champion baseline available - train and register a model first",
            )

        rows = PredictionRepository(session).list_recent(settings.drift_window_size)
        feature_rows = [row.features for row in rows if row.features]
        if len(feature_rows) < settings.drift_min_rows:
            return DriftSummary(
                status="insufficient_data",
                evaluated_rows=len(feature_rows),
                detail=(
                    f"need at least {settings.drift_min_rows} recent predictions with "
                    f"features, have {len(feature_rows)}"
                ),
            )

        threshold = settings.drift_psi_threshold
        statuses: list[DriftStatus] = []
        for column in FEATURE_COLUMNS:
            if column not in baseline:
                continue
            actual = np.array([float(row.get(column, 0.0)) for row in feature_rows])
            psi = psi_from_baseline(baseline[column], actual)
            FRAUD_DRIFT_PSI.labels(feature=column).set(psi)
            statuses.append(
                DriftStatus(
                    feature_name=column,
                    psi_score=psi,
                    threshold=threshold,
                    drift_detected=psi >= threshold,
                )
            )

        timestamps = [row.created_at for row in rows if row.created_at is not None]
        now = datetime.now(UTC)
        window_start = min(timestamps) if timestamps else now
        window_end = max(timestamps) if timestamps else now
        self._persist_reports(session, statuses, window_start, window_end, now)

        drifted = [status for status in statuses if status.drift_detected]
        retraining_triggered = False
        if drifted and settings.auto_retrain_on_drift and self._retraining is not None:
            job = self._retraining.submit(reason=f"drift:{drifted[0].feature_name}", debounce=True)
            retraining_triggered = job is not None
            if retraining_triggered:
                logger.warning(
                    "drift_retraining_triggered",
                    feature=drifted[0].feature_name,
                    psi=drifted[0].psi_score,
                )

        return DriftSummary(
            status="drift_detected" if drifted else "ok",
            features=statuses,
            evaluated_rows=len(feature_rows),
            retraining_triggered=retraining_triggered,
        )

    @staticmethod
    def _persist_reports(
        session: Session,
        statuses: list[DriftStatus],
        window_start: datetime,
        window_end: datetime,
        now: datetime,
    ) -> None:
        """Best-effort persistence of the per-feature drift reports.

        Runs inside a SAVEPOINT so a failure never discards other pending
        writes on the request's session.
        """
        try:
            with session.begin_nested():
                DriftReportRepository(session).add_many(
                    [
                        DriftReport(
                            feature_name=status.feature_name,
                            psi_score=status.psi_score,
                            threshold=status.threshold,
                            drift_detected=status.drift_detected,
                            window_start=window_start,
                            window_end=window_end,
                            created_at=now,
                        )
                        for status in statuses
                    ]
                )
        except Exception:  # noqa: BLE001 - policy: reporting is best-effort
            FRAUD_DB_ERRORS_TOTAL.inc()
            logger.warning("drift_report_persist_failed")
