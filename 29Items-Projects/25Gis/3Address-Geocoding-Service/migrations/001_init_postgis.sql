CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE IF NOT EXISTS addresses (
    id BIGSERIAL PRIMARY KEY,
    formatted_address TEXT NOT NULL,
    normalized_address TEXT NOT NULL,
    source VARCHAR(50) NOT NULL,
    provider_place_id TEXT,
    confidence NUMERIC(5, 4) NOT NULL DEFAULT 0,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    geom GEOGRAPHY(POINT, 4326) GENERATED ALWAYS AS (
        ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)::GEOGRAPHY
    ) STORED,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS address_lookup_events (
    id BIGSERIAL PRIMARY KEY,
    query TEXT NOT NULL,
    normalized_query TEXT NOT NULL,
    address_id BIGINT REFERENCES addresses(id),
    match_status VARCHAR(30) NOT NULL,
    latency_ms INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_addresses_geom ON addresses USING GIST (geom);
CREATE INDEX IF NOT EXISTS idx_addresses_normalized_trgm
    ON addresses USING GIN (normalized_address gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_lookup_events_created_at
    ON address_lookup_events (created_at DESC);
