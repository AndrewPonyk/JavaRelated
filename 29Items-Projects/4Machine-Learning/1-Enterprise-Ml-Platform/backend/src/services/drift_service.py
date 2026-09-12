"""Drift detection: capture inferences, compare against baseline snapshots.

Drift *reports* are durable (repository); baselines and the live inference
window are high-volume telemetry held in the in-memory buffer.
"""
from __future__ import annotations

import logging

from src.core.errors import NotFoundError
from src.db.models import DriftReport, new_id
from src.db.repository import SqlAlchemyRepository
from src.db.telemetry import TelemetryBuffer, get_telemetry
from src.ml.drift.detectors import population_stability_index

logger = logging.getLogger(__name__)

DEFAULT_PSI_THRESHOLD = 0.2


class DriftService:
    """Log production inferences and evaluate feature drift vs. a baseline."""

    def __init__(
        self, repo: SqlAlchemyRepository, telemetry: TelemetryBuffer | None = None
    ) -> None:
        self._repo = repo
        self._tel = telemetry or get_telemetry()

    def set_baseline(self, model_name: str, sample: dict[str, list[float]]) -> None:
        """Register the training-time feature distribution as the baseline."""
        self._tel.set_baseline(model_name, sample)

    async def log_inference(
        self, model_name: str, features: dict[str, object], prediction: object
    ) -> None:
        """Append an inference for later drift evaluation.

        Called fire-and-forget from the serving path; must never raise into the
        request, hence the broad guard around the best-effort write.
        """
        try:
            numeric = {k: float(v) for k, v in features.items() if _is_number(v)}
            if numeric:
                self._tel.append_observation(model_name, numeric)
        except Exception:  # noqa: BLE001 - drift logging is best-effort only
            logger.warning("drift logging failed for model=%s", model_name, exc_info=True)

    async def run_check(
        self, model_name: str, threshold: float = DEFAULT_PSI_THRESHOLD
    ) -> dict:
        """Run a PSI drift check of the live window against the baseline.

        Persists a non-drifted, zero-feature report when there is no baseline or
        no observations yet (a valid early-life edge case, not an error).
        """
        baseline = self._tel.baseline(model_name)
        window = self._tel.window(model_name)
        shared = [f for f in baseline if f in window and baseline[f] and window[f]]

        score = 0.0
        for feature in shared:
            score = max(score, population_stability_index(baseline[feature], window[feature]))

        report = DriftReport(
            id=new_id(),
            model_name=model_name,
            method="PSI",
            score=round(score, 6),
            threshold=threshold,
            drifted=score > threshold,
            evaluated_features=len(shared),
        )
        await self._repo.add_drift_report(report)
        if report.drifted:
            logger.warning("drift detected model=%s score=%.4f", model_name, score)
        return _to_dict(report)

    async def latest(self, model_name: str) -> dict:
        """Return the most recent stored drift report for a model."""
        report = await self._repo.latest_drift_report(model_name)
        if report is None:
            raise NotFoundError(f"no drift report for model: {model_name}")
        return _to_dict(report)


def _is_number(value: object) -> bool:
    return isinstance(value, (int, float)) and not isinstance(value, bool)


def _to_dict(rec: DriftReport) -> dict:
    return {
        "model_name": rec.model_name,
        "method": rec.method,
        "score": rec.score,
        "threshold": rec.threshold,
        "drifted": rec.drifted,
        "evaluated_features": rec.evaluated_features,
    }
