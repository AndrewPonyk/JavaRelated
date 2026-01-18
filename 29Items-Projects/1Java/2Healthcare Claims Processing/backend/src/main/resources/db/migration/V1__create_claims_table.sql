-- V1__create_claims_table.sql
-- Healthcare Claims Processing - Claims Table

CREATE TABLE claims (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    claim_number VARCHAR(20) NOT NULL UNIQUE,
    type VARCHAR(50) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'SUBMITTED',
    amount DECIMAL(12, 2) NOT NULL,
    allowed_amount DECIMAL(12, 2),
    service_date DATE NOT NULL,
    service_end_date DATE,
    patient_id UUID NOT NULL,
    provider_id UUID NOT NULL,
    diagnosis_codes VARCHAR(500),
    procedure_codes VARCHAR(500),
    fraud_score DOUBLE PRECISION,
    fraud_reasons VARCHAR(1000),
    denial_reason VARCHAR(500),
    notes TEXT,
    submitted_by VARCHAR(255),
    reviewed_by VARCHAR(255),
    reviewed_at TIMESTAMP,
    version BIGINT DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_claim_type CHECK (type IN (
        'MEDICAL', 'DENTAL', 'VISION', 'PHARMACY', 'MENTAL_HEALTH',
        'REHABILITATION', 'DURABLE_MEDICAL_EQUIPMENT', 'LABORATORY',
        'RADIOLOGY', 'EMERGENCY', 'INPATIENT', 'OUTPATIENT'
    )),

    CONSTRAINT chk_claim_status CHECK (status IN (
        'SUBMITTED', 'VALIDATING', 'INVALID', 'PENDING_ADJUDICATION',
        'AUTO_ADJUDICATED', 'PENDING_REVIEW', 'FLAGGED_FRAUD',
        'APPROVED', 'DENIED', 'PAID'
    )),

    CONSTRAINT chk_positive_amount CHECK (amount > 0),
    CONSTRAINT chk_service_date_not_future CHECK (service_date <= CURRENT_DATE)
);

-- Indexes for common queries
CREATE INDEX idx_claims_status ON claims(status);
CREATE INDEX idx_claims_patient_id ON claims(patient_id);
CREATE INDEX idx_claims_provider_id ON claims(provider_id);
CREATE INDEX idx_claims_created_at ON claims(created_at DESC);
CREATE INDEX idx_claims_service_date ON claims(service_date);
CREATE INDEX idx_claims_fraud_score ON claims(fraud_score) WHERE fraud_score IS NOT NULL;

-- Trigger to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_claims_updated_at
    BEFORE UPDATE ON claims
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE claims IS 'Healthcare insurance claims submitted for processing';
COMMENT ON COLUMN claims.claim_number IS 'Unique human-readable claim identifier';
COMMENT ON COLUMN claims.fraud_score IS 'ML-generated fraud probability score (0-1)';
