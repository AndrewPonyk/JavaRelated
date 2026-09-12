CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS datasets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(160) NOT NULL,
    description TEXT,
    source_type VARCHAR(50) NOT NULL DEFAULT 'vector',
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT datasets_source_type_check CHECK (source_type IN ('vector', 'raster', 'classification'))
);

CREATE TABLE IF NOT EXISTS dataset_features (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dataset_id UUID NOT NULL REFERENCES datasets(id) ON DELETE CASCADE,
    properties JSONB NOT NULL DEFAULT '{}'::jsonb,
    geom geometry(Geometry, 4326) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS layers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dataset_id UUID NOT NULL REFERENCES datasets(id) ON DELETE CASCADE,
    name VARCHAR(160) NOT NULL,
    layer_type VARCHAR(40) NOT NULL DEFAULT 'wms',
    style VARCHAR(120) NOT NULL DEFAULT 'default',
    is_public BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT layers_layer_type_check CHECK (layer_type IN ('wms', 'wfs', 'deckgl', 'classification'))
);

CREATE TABLE IF NOT EXISTS ml_classification_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dataset_id UUID REFERENCES datasets(id) ON DELETE SET NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'queued',
    model_version VARCHAR(80),
    metrics JSONB NOT NULL DEFAULT '{}'::jsonb,
    artifact_uri TEXT,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ml_classification_jobs_status_check CHECK (status IN ('queued', 'running', 'succeeded', 'failed'))
);

CREATE TABLE IF NOT EXISTS roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(80) UNIQUE NOT NULL,
    description VARCHAR(300),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(320) UNIQUE NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    identity_subject VARCHAR(240) UNIQUE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS user_roles (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

CREATE INDEX IF NOT EXISTS idx_datasets_name ON datasets USING btree (name);
CREATE INDEX IF NOT EXISTS idx_dataset_features_dataset_id ON dataset_features USING btree (dataset_id);
CREATE INDEX IF NOT EXISTS idx_dataset_features_geom ON dataset_features USING gist (geom);
CREATE INDEX IF NOT EXISTS idx_layers_dataset_id ON layers USING btree (dataset_id);
CREATE INDEX IF NOT EXISTS idx_ml_classification_jobs_dataset_id ON ml_classification_jobs USING btree (dataset_id);
CREATE INDEX IF NOT EXISTS idx_users_email ON users USING btree (email);

INSERT INTO roles (name, description)
VALUES
    ('admin', 'Platform administrator'),
    ('analyst', 'GIS analyst')
ON CONFLICT (name) DO NOTHING;
