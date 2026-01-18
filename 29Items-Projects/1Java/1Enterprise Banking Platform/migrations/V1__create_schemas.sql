-- Enterprise Banking Platform Database Schema
-- Version 1: Initial Schema Creation

-- Create schemas for service isolation
CREATE SCHEMA IF NOT EXISTS account_service;
CREATE SCHEMA IF NOT EXISTS transaction_service;
CREATE SCHEMA IF NOT EXISTS fraud_service;
CREATE SCHEMA IF NOT EXISTS loan_service;

-- ===========================================
-- Account Service Tables
-- ===========================================

CREATE TABLE account_service.accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_number VARCHAR(20) NOT NULL UNIQUE,
    owner_id UUID NOT NULL,
    owner_name VARCHAR(255) NOT NULL,
    owner_email VARCHAR(255) NOT NULL,
    account_type VARCHAR(50) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    balance DECIMAL(19,4) NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_ACTIVATION',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_accounts_owner_id ON account_service.accounts(owner_id);
CREATE INDEX idx_accounts_account_number ON account_service.accounts(account_number);
CREATE INDEX idx_accounts_status ON account_service.accounts(status);

CREATE TABLE account_service.account_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL REFERENCES account_service.accounts(id),
    event_type VARCHAR(50) NOT NULL,
    event_data JSONB NOT NULL,
    event_version BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_account_events_account_id ON account_service.account_events(account_id);
CREATE INDEX idx_account_events_created_at ON account_service.account_events(created_at);

-- ===========================================
-- Transaction Service Tables
-- ===========================================

CREATE TABLE transaction_service.transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reference_number VARCHAR(30) NOT NULL UNIQUE,
    source_account_id UUID NOT NULL,
    target_account_id UUID,
    transaction_type VARCHAR(30) NOT NULL,
    amount DECIMAL(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    description TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'INITIATED',
    risk_score DECIMAL(5,4),
    failure_reason TEXT,
    initiated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_transactions_source_account ON transaction_service.transactions(source_account_id);
CREATE INDEX idx_transactions_target_account ON transaction_service.transactions(target_account_id);
CREATE INDEX idx_transactions_reference ON transaction_service.transactions(reference_number);
CREATE INDEX idx_transactions_status ON transaction_service.transactions(status);
CREATE INDEX idx_transactions_initiated_at ON transaction_service.transactions(initiated_at);

-- ===========================================
-- Fraud Detection Service Tables
-- ===========================================

CREATE TABLE fraud_service.fraud_checks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL,
    risk_score DECIMAL(5,4) NOT NULL,
    risk_level VARCHAR(20) NOT NULL,
    risk_factors JSONB,
    recommended_action VARCHAR(30) NOT NULL,
    model_version VARCHAR(20),
    inference_time_ms BIGINT,
    checked_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_fraud_checks_transaction_id ON fraud_service.fraud_checks(transaction_id);
CREATE INDEX idx_fraud_checks_risk_level ON fraud_service.fraud_checks(risk_level);
CREATE INDEX idx_fraud_checks_checked_at ON fraud_service.fraud_checks(checked_at);

CREATE TABLE fraud_service.fraud_alerts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL,
    alert_type VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    description TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    assigned_to VARCHAR(255),
    resolved_at TIMESTAMP WITH TIME ZONE,
    resolution_notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_fraud_alerts_transaction_id ON fraud_service.fraud_alerts(transaction_id);
CREATE INDEX idx_fraud_alerts_status ON fraud_service.fraud_alerts(status);
CREATE INDEX idx_fraud_alerts_severity ON fraud_service.fraud_alerts(severity);

CREATE TABLE fraud_service.velocity_tracking (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL,
    transaction_count_1h INTEGER NOT NULL DEFAULT 0,
    transaction_count_24h INTEGER NOT NULL DEFAULT 0,
    amount_sum_1h DECIMAL(19,4) NOT NULL DEFAULT 0,
    amount_sum_24h DECIMAL(19,4) NOT NULL DEFAULT 0,
    last_transaction_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_velocity_account_id ON fraud_service.velocity_tracking(account_id);

-- ===========================================
-- Loan Service Tables
-- ===========================================

CREATE TABLE loan_service.loan_applications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_number VARCHAR(30) NOT NULL UNIQUE,
    applicant_id UUID NOT NULL,
    applicant_name VARCHAR(255) NOT NULL,
    applicant_email VARCHAR(255) NOT NULL,
    loan_type VARCHAR(30) NOT NULL,
    requested_amount DECIMAL(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    term_months INTEGER NOT NULL,
    annual_income DECIMAL(19,4) NOT NULL,
    employment_status VARCHAR(30) NOT NULL,
    purpose TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    credit_score INTEGER,
    approved_amount DECIMAL(19,4),
    interest_rate DECIMAL(6,4),
    rejection_reason TEXT,
    reviewed_by VARCHAR(255),
    approved_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_loan_applications_applicant_id ON loan_service.loan_applications(applicant_id);
CREATE INDEX idx_loan_applications_status ON loan_service.loan_applications(status);
CREATE INDEX idx_loan_applications_application_number ON loan_service.loan_applications(application_number);

CREATE TABLE loan_service.loans (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    loan_number VARCHAR(30) NOT NULL UNIQUE,
    application_id UUID NOT NULL REFERENCES loan_service.loan_applications(id),
    borrower_id UUID NOT NULL,
    borrower_name VARCHAR(255) NOT NULL,
    loan_type VARCHAR(30) NOT NULL,
    principal_amount DECIMAL(19,4) NOT NULL,
    outstanding_balance DECIMAL(19,4) NOT NULL,
    interest_rate DECIMAL(6,4) NOT NULL,
    term_months INTEGER NOT NULL,
    monthly_payment DECIMAL(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_DISBURSEMENT',
    disbursement_account_id UUID NOT NULL,
    disbursed_at TIMESTAMP WITH TIME ZONE,
    maturity_date DATE,
    next_payment_date DATE,
    next_payment_amount DECIMAL(19,4),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_loans_borrower_id ON loan_service.loans(borrower_id);
CREATE INDEX idx_loans_status ON loan_service.loans(status);
CREATE INDEX idx_loans_loan_number ON loan_service.loans(loan_number);

CREATE TABLE loan_service.loan_payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    loan_id UUID NOT NULL REFERENCES loan_service.loans(id),
    payment_number INTEGER NOT NULL,
    due_date DATE NOT NULL,
    principal_amount DECIMAL(19,4) NOT NULL,
    interest_amount DECIMAL(19,4) NOT NULL,
    total_amount DECIMAL(19,4) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'SCHEDULED',
    paid_amount DECIMAL(19,4),
    paid_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_loan_payments_loan_id ON loan_service.loan_payments(loan_id);
CREATE INDEX idx_loan_payments_due_date ON loan_service.loan_payments(due_date);
CREATE INDEX idx_loan_payments_status ON loan_service.loan_payments(status);

-- ===========================================
-- Functions and Triggers
-- ===========================================

-- Function to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Apply triggers to all tables with updated_at column
CREATE TRIGGER update_accounts_updated_at
    BEFORE UPDATE ON account_service.accounts
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_transactions_updated_at
    BEFORE UPDATE ON transaction_service.transactions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_fraud_alerts_updated_at
    BEFORE UPDATE ON fraud_service.fraud_alerts
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_loan_applications_updated_at
    BEFORE UPDATE ON loan_service.loan_applications
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_loans_updated_at
    BEFORE UPDATE ON loan_service.loans
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ===========================================
-- Sample Data for Development
-- ===========================================

-- Insert sample account
INSERT INTO account_service.accounts (id, account_number, owner_id, owner_name, owner_email, account_type, currency, balance, status)
VALUES
    ('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', '2601-0001-0001', 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'John Doe', 'john.doe@example.com', 'CHECKING', 'USD', 5000.00, 'ACTIVE'),
    ('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a12', '2601-0001-0002', 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'John Doe', 'john.doe@example.com', 'SAVINGS', 'USD', 10000.00, 'ACTIVE'),
    ('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a13', '2601-0002-0001', 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a12', 'Jane Smith', 'jane.smith@example.com', 'CHECKING', 'USD', 7500.00, 'ACTIVE');

-- Grant permissions
GRANT ALL PRIVILEGES ON SCHEMA account_service TO banking_user;
GRANT ALL PRIVILEGES ON SCHEMA transaction_service TO banking_user;
GRANT ALL PRIVILEGES ON SCHEMA fraud_service TO banking_user;
GRANT ALL PRIVILEGES ON SCHEMA loan_service TO banking_user;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA account_service TO banking_user;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA transaction_service TO banking_user;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA fraud_service TO banking_user;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA loan_service TO banking_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA account_service TO banking_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA transaction_service TO banking_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA fraud_service TO banking_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA loan_service TO banking_user;
