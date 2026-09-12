"""Warm the serving cache: daily mart aggregates → Redis daily zsets.

Runs as the last task of etl_daily_batch so dashboards (and redis-mode
`granularity=daily` history queries) are warm before business hours.

Key contract shared with api/app/services/metrics_service.py:
    metric:{name}:daily   zset — member json {value, window_start_ms, window_ms},
                                 score = day start (epoch ms), window = 24 h
"""

from __future__ import annotations

import json
import logging
import time
from datetime import UTC, date, datetime
from datetime import time as dtime

log = logging.getLogger(__name__)

DAY_MS = 86_400_000

YESTERDAY_AGGREGATES_SQL = """
select metric_name, metric_date, metric_value
from analytics.marts.fct_business_metrics_daily
where metric_date >= dateadd('day', -%(days)s, current_date)
order by metric_name, metric_date
"""


def warm_daily_cache(
    rows: list[tuple],
    *,
    redis_url: str = "",
    client=None,
    key_prefix: str = "metric:",
    retention_days: int = 90,
) -> int:
    """Write (metric_name, metric_date, metric_value) rows to the daily zsets.

    Idempotent: members are keyed by day, re-warming overwrites in place.
    Returns the number of points written.
    """
    if client is None:
        import redis  # lazy

        client = redis.Redis.from_url(redis_url, decode_responses=True)

    cutoff_ms = int(time.time() * 1000) - retention_days * DAY_MS
    written = 0
    touched: set[str] = set()

    for metric_name, metric_date, metric_value in rows:
        day: date = metric_date if isinstance(metric_date, date) else metric_date.date()
        day_start_ms = int(datetime.combine(day, dtime.min, tzinfo=UTC).timestamp() * 1000)
        member = json.dumps(
            {
                "metric": metric_name,
                "value": float(metric_value),
                "window_start_ms": day_start_ms,
                "window_ms": DAY_MS,
            }
        )
        key = f"{key_prefix}{metric_name}:daily"
        # Remove any previous member for this day (value may have been restated
        # by the incremental mart), then insert the fresh one.
        client.zremrangebyscore(key, day_start_ms, day_start_ms)
        client.zadd(key, {member: day_start_ms})
        touched.add(key)
        written += 1

    for key in touched:
        client.zremrangebyscore(key, "-inf", cutoff_ms)
        client.expire(key, retention_days * 86_400)

    log.info("warmed %d daily points across %d metrics", written, len(touched))
    return written
