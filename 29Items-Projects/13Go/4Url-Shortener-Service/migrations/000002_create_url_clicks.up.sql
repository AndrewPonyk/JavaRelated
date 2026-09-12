CREATE TABLE IF NOT EXISTS url_clicks (
  id BIGSERIAL PRIMARY KEY,
  url_id UUID NOT NULL REFERENCES urls(id) ON DELETE CASCADE,
  short_code TEXT NOT NULL,
  clicked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  referrer TEXT,
  user_agent TEXT,
  ip_hash TEXT,
  country_code TEXT
);

CREATE INDEX IF NOT EXISTS idx_url_clicks_url_id_clicked_at
  ON url_clicks (url_id, clicked_at DESC);

CREATE INDEX IF NOT EXISTS idx_url_clicks_short_code_clicked_at
  ON url_clicks (short_code, clicked_at DESC);

CREATE TABLE IF NOT EXISTS url_click_rollups_hourly (
  url_id UUID NOT NULL REFERENCES urls(id) ON DELETE CASCADE,
  short_code TEXT NOT NULL,
  bucket_start TIMESTAMPTZ NOT NULL,
  clicks BIGINT NOT NULL DEFAULT 0,
  referrer TEXT NOT NULL DEFAULT '',
  country_code TEXT NOT NULL DEFAULT '',
  PRIMARY KEY (url_id, bucket_start, referrer, country_code)
);
