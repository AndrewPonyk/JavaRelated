package database

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"time"

	_ "github.com/lib/pq"
)

const schemaSQL = `
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS tenants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL UNIQUE,
    status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'suspended')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS gateway_routes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    host TEXT NOT NULL,
    path_prefix TEXT NOT NULL,
    methods TEXT[] NOT NULL DEFAULT '{}',
    upstream_service TEXT NOT NULL,
    upstream_protocol TEXT NOT NULL CHECK (upstream_protocol IN ('http', 'grpc')),
    rate_limit_per_minute INTEGER NOT NULL DEFAULT 0,
    required_scopes TEXT[] NOT NULL DEFAULT '{}',
    transform_headers JSONB NOT NULL DEFAULT '{}',
    anomaly_protection BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, host, path_prefix)
);

CREATE TABLE IF NOT EXISTS anomaly_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    route_id UUID NOT NULL REFERENCES gateway_routes(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    score NUMERIC(5,4) NOT NULL CHECK (score >= 0 AND score <= 1),
    reason TEXT NOT NULL,
    features JSONB NOT NULL,
    observed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_gateway_routes_lookup
    ON gateway_routes (tenant_id, host, path_prefix);

CREATE INDEX IF NOT EXISTS idx_gateway_routes_host_method
    ON gateway_routes (lower(host), path_prefix);

CREATE INDEX IF NOT EXISTS idx_tenants_status
    ON tenants (status);

CREATE INDEX IF NOT EXISTS idx_anomaly_events_route_observed
    ON anomaly_events (route_id, observed_at DESC);

CREATE INDEX IF NOT EXISTS idx_anomaly_events_tenant_observed
    ON anomaly_events (tenant_id, observed_at DESC);

INSERT INTO tenants (name, status)
VALUES ('default', 'active')
ON CONFLICT (name) DO NOTHING;
`

func Connect(ctx context.Context, databaseURL string) (*sql.DB, error) {
	db, err := sql.Open("postgres", databaseURL)
	if err != nil {
		return nil, err
	}
	db.SetMaxOpenConns(20)
	db.SetMaxIdleConns(10)
	db.SetConnMaxLifetime(30 * time.Minute)

	if err := retryPing(ctx, db); err != nil {
		_ = db.Close()
		return nil, err
	}
	return db, nil
}

func Migrate(ctx context.Context, db *sql.DB) error {
	_, err := db.ExecContext(ctx, schemaSQL)
	if err != nil {
		return fmt.Errorf("apply database schema: %w", err)
	}
	return nil
}

func retryPing(ctx context.Context, db *sql.DB) error {
	var lastErr error
	for attempt := 0; attempt < 20; attempt++ {
		if err := db.PingContext(ctx); err == nil {
			return nil
		} else {
			lastErr = err
		}

		select {
		case <-ctx.Done():
			return errors.Join(ctx.Err(), lastErr)
		case <-time.After(time.Second):
		}
	}
	return fmt.Errorf("database ping failed: %w", lastErr)
}
