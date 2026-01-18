-- V3__create_patients_table.sql
-- Healthcare Claims Processing - Patients Table

CREATE TABLE patients (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    member_id VARCHAR(20) NOT NULL UNIQUE,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    date_of_birth DATE NOT NULL,
    policy_number VARCHAR(30) NOT NULL,
    group_number VARCHAR(20),
    policy_start_date DATE,
    policy_end_date DATE,
    email VARCHAR(255),
    phone VARCHAR(20),
    address VARCHAR(500),
    city VARCHAR(100),
    state VARCHAR(50),
    zip_code VARCHAR(10),
    is_active BOOLEAN DEFAULT TRUE,
    version BIGINT DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_dob_past CHECK (date_of_birth < CURRENT_DATE),
    CONSTRAINT chk_policy_dates CHECK (
        policy_end_date IS NULL OR
        policy_start_date IS NULL OR
        policy_end_date >= policy_start_date
    )
);

-- Indexes
CREATE INDEX idx_patients_member_id ON patients(member_id);
CREATE INDEX idx_patients_policy_number ON patients(policy_number);
CREATE INDEX idx_patients_name ON patients(last_name, first_name);
CREATE INDEX idx_patients_active ON patients(is_active) WHERE is_active = TRUE;

-- Trigger for updated_at
CREATE TRIGGER trigger_patients_updated_at
    BEFORE UPDATE ON patients
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Add foreign key to claims table
ALTER TABLE claims
    ADD CONSTRAINT fk_claims_patient
    FOREIGN KEY (patient_id) REFERENCES patients(id);

COMMENT ON TABLE patients IS 'Insurance members/patients';
COMMENT ON COLUMN patients.member_id IS 'Unique member identifier from enrollment system';
