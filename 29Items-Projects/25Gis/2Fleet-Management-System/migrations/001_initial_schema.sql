CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS drivers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(120) NOT NULL,
    phone VARCHAR(40),
    email VARCHAR(255) UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS vehicles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(120) NOT NULL,
    license_plate VARCHAR(32) NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL DEFAULT 'idle',
    driver_id UUID REFERENCES drivers(id) ON DELETE SET NULL,
    latest_latitude DOUBLE PRECISION,
    latest_longitude DOUBLE PRECISION,
    latest_speed_kph DOUBLE PRECISION,
    latest_heading_degrees DOUBLE PRECISION,
    latest_position GEOGRAPHY(POINT, 4326),
    last_seen_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS geofences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(160) NOT NULL,
    description TEXT,
    boundary_geojson JSONB NOT NULL,
    boundary GEOGRAPHY(POLYGON, 4326),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS vehicle_location_history (
    id BIGSERIAL PRIMARY KEY,
    vehicle_id UUID NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE,
    recorded_at TIMESTAMPTZ NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    speed_kph DOUBLE PRECISION,
    heading_degrees DOUBLE PRECISION,
    position GEOGRAPHY(POINT, 4326),
    raw_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT uq_vehicle_location_recorded_at UNIQUE (vehicle_id, recorded_at)
);

CREATE TABLE IF NOT EXISTS trips (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vehicle_id UUID NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE,
    name VARCHAR(160) NOT NULL,
    origin_latitude DOUBLE PRECISION NOT NULL,
    origin_longitude DOUBLE PRECISION NOT NULL,
    destination_latitude DOUBLE PRECISION NOT NULL,
    destination_longitude DOUBLE PRECISION NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'planned',
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS route_predictions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vehicle_id UUID NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE,
    origin_latitude DOUBLE PRECISION NOT NULL,
    origin_longitude DOUBLE PRECISION NOT NULL,
    destination_latitude DOUBLE PRECISION NOT NULL,
    destination_longitude DOUBLE PRECISION NOT NULL,
    origin GEOGRAPHY(POINT, 4326),
    destination GEOGRAPHY(POINT, 4326),
    distance_km DOUBLE PRECISION NOT NULL,
    eta_seconds INTEGER NOT NULL,
    confidence DOUBLE PRECISION NOT NULL,
    model_version VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS geofence_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vehicle_id UUID NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE,
    geofence_id UUID NOT NULL REFERENCES geofences(id) ON DELETE CASCADE,
    event_type VARCHAR(32) NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_vehicles_latest_position_gist
    ON vehicles USING GIST (latest_position);

CREATE INDEX IF NOT EXISTS idx_geofences_boundary_gist
    ON geofences USING GIST (boundary);

CREATE INDEX IF NOT EXISTS idx_vehicle_location_history_vehicle_time
    ON vehicle_location_history (vehicle_id, recorded_at DESC);

CREATE INDEX IF NOT EXISTS idx_vehicle_location_history_position_gist
    ON vehicle_location_history USING GIST (position);

CREATE INDEX IF NOT EXISTS idx_trips_vehicle_id
    ON trips (vehicle_id);

CREATE INDEX IF NOT EXISTS idx_route_predictions_vehicle_id
    ON route_predictions (vehicle_id);

CREATE INDEX IF NOT EXISTS idx_geofence_events_vehicle_time
    ON geofence_events (vehicle_id, recorded_at DESC);
