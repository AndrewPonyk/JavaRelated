"""Anomaly detection: Prophet forecast bands, z-score fallback.

Prophet is an optional dependency (`pip install .[ml]`): fits are CPU-bound
and belong in the worker, not the API image. The z-score path keeps detection
alive on any install (docs/TECH-NOTES.md §3.6 pitfall 5).
"""

from __future__ import annotations

import logging
import statistics
from collections.abc import Sequence
from datetime import datetime

from app.core.config import settings
from app.schemas.anomaly import Anomaly

logger = logging.getLogger(__name__)

History = Sequence[tuple[datetime, float]]  # time-ordered (ts, value) samples


def zscore_anomalies(
    device_id: str,
    metric: str,
    history: History,
    threshold: float | None = None,
) -> list[Anomaly]:
    """Flag points whose |z| exceeds the threshold. Pure, unit-tested."""
    limit = threshold if threshold is not None else settings.zscore_threshold
    if len(history) < 3:
        return []

    values = [v for _, v in history]
    mean = statistics.fmean(values)
    std = statistics.pstdev(values)
    if std == 0.0:
        return []

    found: list[Anomaly] = []
    for ts, value in history:
        z = (value - mean) / std
        if abs(z) >= limit:
            found.append(
                Anomaly(
                    device_id=device_id,
                    metric=metric,
                    ts=ts,
                    value=value,
                    expected=mean,
                    lower=mean - limit * std,
                    upper=mean + limit * std,
                    score=abs(z),
                    method="zscore",
                )
            )
    return found


def prophet_anomalies(
    device_id: str,
    metric: str,
    history: History,
    interval_width: float | None = None,
) -> list[Anomaly]:
    """Fit Prophet on the series and flag points outside the forecast band.

    Raises ImportError when prophet/pandas are not installed (caller decides
    whether to fall back). Fit on ROLLUPS (≤ a few hundred points), never raw.
    """
    import pandas as pd
    from prophet import Prophet

    width = interval_width if interval_width is not None else settings.prophet_interval_width
    frame = pd.DataFrame(
        {
            # Prophet requires tz-naive timestamps; series is UTC by contract.
            "ds": [ts.replace(tzinfo=None) for ts, _ in history],
            "y": [v for _, v in history],
        }
    )

    # Default seasonality; per-metric tuning is a documented future item
    # (docs/PROJECT-PLAN.md Phase 3), not needed for band-based flagging.
    model = Prophet(interval_width=width)
    model.fit(frame)
    forecast = model.predict(frame[["ds"]])

    found: list[Anomaly] = []
    rows = zip(
        history,
        forecast["yhat"],
        forecast["yhat_lower"],
        forecast["yhat_upper"],
        strict=False,
    )
    for (ts, value), yhat, lower, upper in rows:
        if value < lower or value > upper:
            band = (upper - lower) or 1.0
            found.append(
                Anomaly(
                    device_id=device_id,
                    metric=metric,
                    ts=ts,
                    value=value,
                    expected=float(yhat),
                    lower=float(lower),
                    upper=float(upper),
                    score=abs(value - float(yhat)) / band,
                    method="prophet",
                )
            )
    return found


def detect(device_id: str, metric: str, history: History) -> list[Anomaly]:
    """Entry point used by the anomaly worker; honors DETECTION_METHOD."""
    if len(history) < settings.min_history_points:
        logger.debug("skip %s/%s: %d < min history", device_id, metric, len(history))
        return []

    method = settings.detection_method
    if method in ("auto", "prophet"):
        try:
            return prophet_anomalies(device_id, metric, history)
        except ImportError:
            if method == "prophet":
                raise
            logger.info("prophet not installed — falling back to zscore")
        except Exception:
            logger.warning(
                "prophet failed for %s/%s — falling back to zscore",
                device_id,
                metric,
                exc_info=True,
            )
    return zscore_anomalies(device_id, metric, history)
