-- 004: dashboard users + series catalog

-- Dashboard users (JWT credential plane). Passwords stored as
-- pbkdf2_sha256$iterations$salt$hash — never plaintext.
-- The bootstrap admin (ADMIN_USERNAME/ADMIN_PASSWORD) is inserted by the API
-- at startup with IF NOT EXISTS, so an existing row is never overwritten.
CREATE TABLE IF NOT EXISTS tsa.users (
    username      text PRIMARY KEY,
    password_hash text,
    roles         set<text>,     -- viewer | operator | admin
    created_at    timestamp
);

-- Which metrics each device has ever reported. Maintained by the ingest path
-- (idempotent upsert); read by the workers for per-device metric discovery
-- and by GET /devices/{id}/metrics for the dashboard's metric picker.
CREATE TABLE IF NOT EXISTS tsa.series_catalog (
    device_id text,
    metric    text,
    PRIMARY KEY (device_id, metric)
);
