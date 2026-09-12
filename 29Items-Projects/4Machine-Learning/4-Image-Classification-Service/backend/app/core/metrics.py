"""Prometheus metrics. Exposed at ``GET /metrics``.

Centralizes the counters/histograms referenced in ARCHITECTURE.md (latency, cache-hit
ratio, error rate, model version).
"""

from __future__ import annotations

from prometheus_client import CONTENT_TYPE_LATEST, Counter, Gauge, Histogram, generate_latest

REQUESTS = Counter(
    "ics_requests_total",
    "Total HTTP requests.",
    labelnames=("method", "path", "status"),
)

CLASSIFY_LATENCY = Histogram(
    "ics_classify_latency_seconds",
    "End-to-end classification latency.",
    labelnames=("cache",),
    buckets=(0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0, 2.5),
)

CACHE_EVENTS = Counter(
    "ics_cache_events_total",
    "Cache hit/miss events.",
    labelnames=("result",),  # hit | miss
)

MODEL_INFO = Gauge(
    "ics_model_loaded",
    "1 if the model is loaded and the service is ready, else 0.",
)


def render_latest() -> tuple[bytes, str]:
    """Return (body, content_type) for the /metrics endpoint."""
    return generate_latest(), CONTENT_TYPE_LATEST
