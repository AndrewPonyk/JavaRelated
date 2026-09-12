-- 003: device registry + anomaly results

-- Small registry table (one row per device; full scan acceptable for a small
-- fleet — add devices_by_site when it is not; see repositories/devices.py).
CREATE TABLE IF NOT EXISTS tsa.devices (
    device_id    text PRIMARY KEY,
    name         text,
    site         text,
    device_type  text,
    api_key_hash text,       -- sha256 of the ingest key; plaintext never stored
    enabled      boolean,
    created_at   timestamp
);

-- Detected anomalies: 90-day retention, partitioned by device + day so the
-- dashboard's "recent anomalies for device X" is a single-partition read.
CREATE TABLE IF NOT EXISTS tsa.anomalies (
    device_id text,
    bucket    date,          -- day of ts (UTC)
    ts        timestamp,
    metric    text,
    value     double,
    expected  double,
    lower     double,
    upper     double,
    score     double,
    method    text,          -- 'prophet' | 'zscore'
    PRIMARY KEY ((device_id, bucket), ts, metric)
) WITH CLUSTERING ORDER BY (ts DESC, metric ASC)
  AND default_time_to_live = 7776000        -- 90 days
  AND compaction = {
        'class': 'TimeWindowCompactionStrategy',
        'compaction_window_unit': 'DAYS',
        'compaction_window_size': 7
      }
  AND comment = 'Anomaly worker output; consumed by API /anomalies endpoints';
