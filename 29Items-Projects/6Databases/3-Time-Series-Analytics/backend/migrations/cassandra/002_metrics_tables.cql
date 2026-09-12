-- 002: time-series tables
-- Design notes (docs/ARCHITECTURE.md §2.4, docs/TECH-NOTES.md §3.6):
--  * Partition = (device_id, metric, time bucket) → bounded partitions forever.
--  * TWCS window ≈ bucket size; expiry = whole-SSTable drop, not tombstone churn.
--  * default_time_to_live is the retention contract; do NOT mix per-write TTLs
--    wildly different from it in these tables.

-- Raw samples: 30-day retention, day partitions.
-- At 1 Hz a partition holds ~86,400 rows/day — comfortably bounded.
CREATE TABLE IF NOT EXISTS tsa.metrics_raw (
    device_id text,
    metric    text,
    bucket    date,          -- day of ts (UTC)
    ts        timestamp,
    value     double,
    tags      map<text, text>,
    PRIMARY KEY ((device_id, metric, bucket), ts)
) WITH CLUSTERING ORDER BY (ts DESC)
  AND default_time_to_live = 2592000        -- 30 days
  AND compaction = {
        'class': 'TimeWindowCompactionStrategy',
        'compaction_window_unit': 'DAYS',
        'compaction_window_size': 1
      }
  AND comment = 'Raw IoT samples; retention via TTL + TWCS SSTable drop';
-- gc_grace_seconds left at default; revisit in ops review (pure-TTL TWCS
-- tables can often lower it, but never below the hint window).

-- Hourly rollups: 365-day retention, month partitions ('YYYY-MM').
-- ~744 rows/month per series; feeds long-range charts and Prophet history.
CREATE TABLE IF NOT EXISTS tsa.metrics_rollup_1h (
    device_id text,
    metric    text,
    bucket    text,          -- 'YYYY-MM' of ts (UTC)
    ts        timestamp,     -- hour start
    min       double,
    max       double,
    avg       double,
    sum       double,
    count     bigint,
    PRIMARY KEY ((device_id, metric, bucket), ts)
) WITH CLUSTERING ORDER BY (ts DESC)
  AND default_time_to_live = 31536000       -- 365 days
  AND compaction = {
        'class': 'TimeWindowCompactionStrategy',
        'compaction_window_unit': 'DAYS',
        'compaction_window_size': 30
      }
  AND comment = 'Hourly aggregates written by the downsampler worker';
