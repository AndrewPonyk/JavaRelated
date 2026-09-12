"""In-memory telemetry buffer for high-volume, non-durable signals.

Holds raw A/B outcome samples, drift baselines, and the live inference window —
data that in production lives in Redis / S3 / a feature store, NOT the metadata
database. Kept here behind a small interface so it can be swapped without
touching the services. Thread-safe via a coarse lock.
"""
from __future__ import annotations

import threading


class TelemetryBuffer:
    """Process-wide ephemeral buffers keyed by model name."""

    def __init__(self) -> None:
        self._lock = threading.RLock()
        # model -> {"champion": [...], "challenger": [...]}
        self._ab_outcomes: dict[str, dict[str, list[float]]] = {}
        # model -> feature -> baseline values
        self._drift_baselines: dict[str, dict[str, list[float]]] = {}
        # model -> feature -> live-window values
        self._drift_window: dict[str, dict[str, list[float]]] = {}

    # -- A/B outcomes -----------------------------------------------------
    def record_ab_outcome(self, model_name: str, variant: str, value: float) -> None:
        with self._lock:
            buckets = self._ab_outcomes.setdefault(
                model_name, {"champion": [], "challenger": []}
            )
            buckets.setdefault(variant, []).append(value)

    def ab_outcomes(self, model_name: str) -> tuple[list[float], list[float]]:
        with self._lock:
            buckets = self._ab_outcomes.get(model_name, {})
            return list(buckets.get("champion", [])), list(buckets.get("challenger", []))

    # -- drift ------------------------------------------------------------
    def set_baseline(self, model_name: str, sample: dict[str, list[float]]) -> None:
        with self._lock:
            self._drift_baselines[model_name] = {k: list(v) for k, v in sample.items()}

    def baseline(self, model_name: str) -> dict[str, list[float]]:
        with self._lock:
            return self._drift_baselines.get(model_name, {})

    def append_observation(self, model_name: str, features: dict[str, float]) -> None:
        with self._lock:
            window = self._drift_window.setdefault(model_name, {})
            for key, value in features.items():
                window.setdefault(key, []).append(value)

    def window(self, model_name: str) -> dict[str, list[float]]:
        with self._lock:
            return self._drift_window.get(model_name, {})

    def reset(self) -> None:
        """Clear all buffers (test isolation)."""
        with self._lock:
            self._ab_outcomes.clear()
            self._drift_baselines.clear()
            self._drift_window.clear()


_buffer = TelemetryBuffer()


def get_telemetry() -> TelemetryBuffer:
    return _buffer
