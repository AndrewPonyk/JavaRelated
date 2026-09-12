"""Canonical Prometheus metrics for the fraud detection service.

The metric names defined here are part of the service contract - Grafana
dashboards and alert rules reference them by name. Do not rename them.
Falls back to no-op stubs when ``prometheus_client`` is unavailable so the
application stays importable in minimal environments.
"""

from __future__ import annotations

try:
    from prometheus_client import (
        CONTENT_TYPE_LATEST,
        Counter,
        Gauge,
        Histogram,
        generate_latest,
    )

    PROMETHEUS_AVAILABLE = True
except ImportError:  # pragma: no cover - prometheus_client is a hard dependency in prod
    PROMETHEUS_AVAILABLE = False
    CONTENT_TYPE_LATEST = "text/plain; version=0.0.4; charset=utf-8"

    class _NoOpMetric:
        """Minimal stand-in matching the prometheus_client metric API."""

        def __init__(self, *args: object, **kwargs: object) -> None:
            pass

        def labels(self, *args: object, **kwargs: object) -> _NoOpMetric:
            return self

        def inc(self, amount: float = 1.0) -> None:
            pass

        def observe(self, value: float) -> None:
            pass

        def set(self, value: float) -> None:
            pass

    Counter = Gauge = Histogram = _NoOpMetric  # type: ignore[assignment,misc]

    def generate_latest() -> bytes:  # type: ignore[misc]
        return b"# prometheus_client not installed\n"


FRAUD_PREDICTIONS_TOTAL = Counter(
    "fraud_predictions_total",
    "Total fraud predictions served, by A/B variant, model version and outcome.",
    ["variant", "model_version", "outcome"],
)

FRAUD_PREDICTION_LATENCY_SECONDS = Histogram(
    "fraud_prediction_latency_seconds",
    "Model inference latency in seconds.",
    buckets=(0.001, 0.0025, 0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0, 2.5),
)

FRAUD_DRIFT_PSI = Gauge(
    "fraud_drift_psi",
    "Latest Population Stability Index per monitored feature.",
    ["feature"],
)

FRAUD_MODEL_FALLBACK_TOTAL = Counter(
    "fraud_model_fallback_total",
    "Times the service fell back to the heuristic stub model.",
)

FRAUD_DB_ERRORS_TOTAL = Counter(
    "fraud_db_errors_total",
    "Database errors swallowed by the best-effort persistence policy.",
)

FRAUD_RETRAINING_RUNS_TOTAL = Counter(
    "fraud_retraining_runs_total",
    "Completed retraining runs by terminal status.",
    ["status"],
)

HTTP_REQUESTS_TOTAL = Counter(
    "http_requests_total",
    "Total HTTP requests processed.",
    ["method", "path", "status"],
)

HTTP_REQUEST_DURATION_SECONDS = Histogram(
    "http_request_duration_seconds",
    "HTTP request duration in seconds.",
    ["method", "path"],
)


def render_metrics() -> tuple[bytes, str]:
    """Render the current metrics in Prometheus text exposition format.

    Returns:
        Tuple of (payload bytes, content type header value).
    """
    return generate_latest(), CONTENT_TYPE_LATEST
