-- V002: seed the metric registry with the demo metrics produced by scripts/seed-events.py.
-- Kept in sync with MetricsService (analytics-api) until definitions are DB-backed.

INSERT INTO metric_definitions (metric_key, display_name, unit, description)
VALUES
    ('orders.completed',  'Orders completed',  'count', 'Successfully completed checkout orders'),
    ('payments.captured', 'Payments captured', 'EUR',   'Captured payment amounts'),
    ('users.signup',      'User sign-ups',     'count', 'New account registrations')
ON CONFLICT (metric_key) DO NOTHING;
