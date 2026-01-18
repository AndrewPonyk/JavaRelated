-- V2__create_providers_table.sql
-- Healthcare Claims Processing - Providers Table

CREATE TABLE providers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    npi VARCHAR(10) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    specialty VARCHAR(100),
    tax_id VARCHAR(20),
    in_network BOOLEAN DEFAULT TRUE,
    provider_type VARCHAR(50),
    email VARCHAR(255),
    phone VARCHAR(20),
    address VARCHAR(500),
    city VARCHAR(100),
    state VARCHAR(50),
    zip_code VARCHAR(10),
    is_active BOOLEAN DEFAULT TRUE,
    credentialing_status VARCHAR(20) DEFAULT 'ACTIVE',
    fraud_risk_score DOUBLE PRECISION,
    version BIGINT DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_npi_format CHECK (npi ~ '^\d{10}$'),
    CONSTRAINT chk_credentialing_status CHECK (credentialing_status IN (
        'ACTIVE', 'PENDING', 'SUSPENDED', 'REVOKED', 'UNDER_REVIEW'
    ))
);

-- Indexes
CREATE INDEX idx_providers_npi ON providers(npi);
CREATE INDEX idx_providers_tax_id ON providers(tax_id);
CREATE INDEX idx_providers_specialty ON providers(specialty);
CREATE INDEX idx_providers_in_network ON providers(in_network) WHERE in_network = TRUE;
CREATE INDEX idx_providers_active ON providers(is_active) WHERE is_active = TRUE;

-- Trigger for updated_at
CREATE TRIGGER trigger_providers_updated_at
    BEFORE UPDATE ON providers
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Add foreign key to claims table
ALTER TABLE claims
    ADD CONSTRAINT fk_claims_provider
    FOREIGN KEY (provider_id) REFERENCES providers(id);

COMMENT ON TABLE providers IS 'Healthcare providers (doctors, hospitals, clinics)';
COMMENT ON COLUMN providers.npi IS 'National Provider Identifier - 10 digit unique ID';
COMMENT ON COLUMN providers.fraud_risk_score IS 'Historical fraud risk assessment score';
