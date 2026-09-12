"""Synthetic log traffic with known-normal shape (plus optional injected incidents).

Used by `ml/train.py --source synthetic` to bootstrap a first model before real history
exists, and by tests that need a realistic feature distribution. Deterministic per seed.
"""

from __future__ import annotations

from datetime import datetime, timedelta, timezone

import numpy as np
import pandas as pd

DEFAULT_SERVICES: tuple[str, ...] = ("checkout", "payments", "inventory", "auth")

_NORMAL_TEMPLATES = (
    ("INFO", "request completed method=GET path=/api/v1/{svc} status=200 duration_ms={n}"),
    ("INFO", "request completed method=POST path=/api/v1/{svc} status=201 duration_ms={n}"),
    ("DEBUG", "cache hit key={svc}:item:{n}"),
    ("INFO", "background job finished job={svc}-sync items={n}"),
    ("WARN", "slow query detected duration_ms={n} table={svc}_events"),
)
_NORMAL_WEIGHTS = np.array([0.35, 0.25, 0.20, 0.13, 0.05])

_ERROR_TEMPLATES = (
    ("ERROR", "upstream timeout calling {svc}-db after {n}ms"),
    ("ERROR", "failed to persist event id={n}: version conflict"),
    ("FATAL", "circuit breaker OPEN for dependency {svc}-gateway"),
)


def generate_log_records(
    minutes: int = 360,
    services: tuple[str, ...] = DEFAULT_SERVICES,
    events_per_minute: int = 40,
    error_rate: float = 0.02,
    seed: int = 7,
    incident_minutes: dict[str, list[int]] | None = None,
    end: datetime | None = None,
) -> pd.DataFrame:
    """Raw log records (timestamp, service, level, message) for `minutes` of traffic.

    `incident_minutes` injects error storms: {"checkout": [100, 101, 102]} makes those
    minutes look like an outage (5x volume, ~40% errors) — useful to sanity-check that
    a trained detector actually scores incidents high.
    """
    rng = np.random.default_rng(seed)
    incidents = incident_minutes or {}
    end_time = (end or datetime.now(timezone.utc)).replace(second=0, microsecond=0)
    start_time = end_time - timedelta(minutes=minutes)

    normal_weights = _NORMAL_WEIGHTS * (1.0 - error_rate)
    weights = np.concatenate([normal_weights, np.full(len(_ERROR_TEMPLATES), error_rate / 3)])
    weights = weights / weights.sum()
    templates = _NORMAL_TEMPLATES + _ERROR_TEMPLATES

    rows: list[dict] = []
    for minute in range(minutes):
        minute_start = start_time + timedelta(minutes=minute)
        for svc_index, service in enumerate(services):
            in_incident = minute in incidents.get(service, [])
            base = events_per_minute * (0.6 + 0.2 * svc_index)  # services differ in volume
            count = int(rng.poisson(base * (5.0 if in_incident else 1.0)))
            if count == 0:
                continue
            probabilities = _incident_weights(weights) if in_incident else weights
            choices = rng.choice(len(templates), size=count, p=probabilities)
            offsets = rng.uniform(0, 60, size=count)
            for template_index, offset in zip(choices, offsets, strict=False):
                level, template = templates[template_index]
                rows.append(
                    {
                        "timestamp": minute_start + timedelta(seconds=float(offset)),
                        "service": service,
                        "level": level,
                        "message": template.format(svc=service, n=int(rng.integers(1, 5000))),
                    }
                )
    return pd.DataFrame(rows, columns=["timestamp", "service", "level", "message"])


def _incident_weights(normal: np.ndarray) -> np.ndarray:
    """Shift ~40% of probability mass onto the error templates."""
    weights = normal.copy()
    n_error = len(_ERROR_TEMPLATES)
    weights[:-n_error] *= 0.6 / weights[:-n_error].sum()
    weights[-n_error:] = 0.4 / n_error
    return weights / weights.sum()
