"""Online anomaly detection: per-metric EWMA baseline + z-score.

Baseline model — deliberately simple, explainable, and dependency-free:
  * EWMA mean/variance per metric (O(1) memory, O(1) update)
  * anomaly when |x - mean| / std >= z_threshold after a warmup period
  * the baseline is NOT updated on anomalous points (spikes don't poison it);
    a sustained legitimate level shift therefore keeps alerting — the weekly
    retrain DAG (anomaly_model_retrain) adapts per-metric parameters instead:
    it publishes tuned overrides which the processor hot-reloads at runtime
    (see model_reloader.py). Parameter fitting lives in
    airflow/dags/common/anomaly_training.py and must stay numerically in sync
    with this scoring rule.
"""

from __future__ import annotations

import math
from dataclasses import dataclass


@dataclass(frozen=True)
class AnomalyResult:
    metric: str
    value: float
    score: float  # robust z-score against the EWMA baseline
    is_anomaly: bool
    baseline_mean: float


@dataclass
class _MetricState:
    mean: float
    var: float = 0.0
    n: int = 1


class EwmaAnomalyDetector:
    def __init__(
        self,
        alpha: float = 0.05,
        z_threshold: float = 4.0,
        warmup: int = 120,
        min_std: float = 1e-6,
    ) -> None:
        if not 0.0 < alpha < 1.0:
            raise ValueError("alpha must be in (0, 1)")
        self.alpha = alpha
        self.z_threshold = z_threshold
        self.warmup = warmup
        self.min_std = min_std
        self._states: dict[str, _MetricState] = {}
        # Per-metric parameter overrides published by the retrain loop:
        #   {metric: {"z_threshold": float, "warmup": int, "alpha": float}}
        self._overrides: dict[str, dict] = {}

    def apply_overrides(self, overrides: dict[str, dict]) -> None:
        """Hot-swap tuned per-metric parameters (validated, unknown keys ignored)."""
        cleaned: dict[str, dict] = {}
        for metric, params in overrides.items():
            entry: dict = {}
            z = params.get("z_threshold")
            if isinstance(z, int | float) and z > 0:
                entry["z_threshold"] = float(z)
            warmup = params.get("warmup")
            if isinstance(warmup, int) and warmup > 0:
                entry["warmup"] = warmup
            alpha = params.get("alpha")
            if isinstance(alpha, int | float) and 0.0 < alpha < 1.0:
                entry["alpha"] = float(alpha)
            if entry:
                cleaned[metric] = entry
        self._overrides = cleaned

    def _params_for(self, metric: str) -> tuple[float, float, int]:
        override = self._overrides.get(metric, {})
        return (
            override.get("alpha", self.alpha),
            override.get("z_threshold", self.z_threshold),
            override.get("warmup", self.warmup),
        )

    def score(self, metric: str, value: float) -> AnomalyResult:
        state = self._states.get(metric)
        if state is None:
            self._states[metric] = _MetricState(mean=value)
            return AnomalyResult(metric, value, 0.0, False, value)

        alpha, z_threshold, warmup = self._params_for(metric)
        std = max(math.sqrt(state.var), self.min_std)
        warmed = state.n >= warmup
        z = abs(value - state.mean) / std if warmed else 0.0
        is_anomaly = warmed and z >= z_threshold

        if not is_anomaly:
            # Standard EWMA mean/variance update (West 1979 incremental form).
            diff = value - state.mean
            incr = alpha * diff
            state.mean += incr
            state.var = (1.0 - alpha) * (state.var + diff * incr)
        state.n += 1

        return AnomalyResult(metric, value, z, is_anomaly, state.mean)
