-- V5__create_audit_log_table.sql
-- Healthcare Claims Processing - Audit Log Table

CREATE TABLE audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type VARCHAR(50) NOT NULL,
    entity_id UUID NOT NULL,
    action VARCHAR(20) NOT NULL,
    user_id VARCHAR(255),
    user_email VARCHAR(255),
    ip_address VARCHAR(45),
    old_values JSONB,
    new_values JSONB,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_action CHECK (action IN (
        'CREATE', 'UPDATE', 'DELETE', 'VIEW', 'APPROVE', 'DENY', 'PROCESS'
    ))
);

-- Indexes for audit queries
CREATE INDEX idx_audit_entity ON audit_log(entity_type, entity_id);
CREATE INDEX idx_audit_user ON audit_log(user_id);
CREATE INDEX idx_audit_action ON audit_log(action);
CREATE INDEX idx_audit_created_at ON audit_log(created_at DESC);

-- Partitioning by month for performance (optional - for high-volume systems)
-- CREATE TABLE audit_log_y2024m01 PARTITION OF audit_log
--     FOR VALUES FROM ('2024-01-01') TO ('2024-02-01');

COMMENT ON TABLE audit_log IS 'Immutable audit trail for all claim-related actions';
COMMENT ON COLUMN audit_log.old_values IS 'JSON snapshot of entity before change';
COMMENT ON COLUMN audit_log.new_values IS 'JSON snapshot of entity after change';

-- Function to create audit log entry
CREATE OR REPLACE FUNCTION log_claim_changes()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        INSERT INTO audit_log (entity_type, entity_id, action, new_values)
        VALUES ('CLAIM', NEW.id, 'CREATE', row_to_json(NEW)::jsonb);
    ELSIF TG_OP = 'UPDATE' THEN
        INSERT INTO audit_log (entity_type, entity_id, action, old_values, new_values)
        VALUES ('CLAIM', NEW.id, 'UPDATE', row_to_json(OLD)::jsonb, row_to_json(NEW)::jsonb);
    ELSIF TG_OP = 'DELETE' THEN
        INSERT INTO audit_log (entity_type, entity_id, action, old_values)
        VALUES ('CLAIM', OLD.id, 'DELETE', row_to_json(OLD)::jsonb);
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Enable audit logging for claims table
CREATE TRIGGER trigger_claims_audit
    AFTER INSERT OR UPDATE OR DELETE ON claims
    FOR EACH ROW
    EXECUTE FUNCTION log_claim_changes();

-- Create processed_events table for idempotent consumers
CREATE TABLE processed_events (
    id UUID PRIMARY KEY,
    processed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_processed_events_processed_at ON processed_events(processed_at);

COMMENT ON TABLE processed_events IS 'Tracks processed Kafka events for idempotency';
