-- Enterprise Banking Platform - Initial Schema
-- Flyway migration V1
-- Creates core tables for accounts and transactions

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ============================================
-- ACCOUNTS TABLE
-- ============================================
CREATE TABLE accounts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    account_number VARCHAR(20) NOT NULL UNIQUE,
    owner_name VARCHAR(100) NOT NULL,
    owner_email VARCHAR(255) NOT NULL,
    account_type VARCHAR(30) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    balance DECIMAL(19, 4) NOT NULL DEFAULT 0.0000,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING_ACTIVATION',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT chk_account_type CHECK (account_type IN (
        'CHECKING', 'SAVINGS', 'BUSINESS', 'MONEY_MARKET', 'CERTIFICATE_OF_DEPOSIT'
    )),
    CONSTRAINT chk_account_status CHECK (status IN (
        'PENDING_ACTIVATION', 'ACTIVE', 'FROZEN', 'DORMANT', 'CLOSED'
    )),
    CONSTRAINT chk_balance_non_negative CHECK (balance >= 0)
);

-- Indexes for accounts
CREATE INDEX idx_accounts_owner_email ON accounts(owner_email);
CREATE INDEX idx_accounts_status ON accounts(status);
CREATE INDEX idx_accounts_account_number ON accounts(account_number);

-- ============================================
-- TRANSACTIONS TABLE
-- ============================================
CREATE TABLE transactions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    reference_number VARCHAR(30) NOT NULL UNIQUE,
    source_account_id UUID NOT NULL REFERENCES accounts(id),
    target_account_id UUID NOT NULL REFERENCES accounts(id),
    transaction_type VARCHAR(30) NOT NULL,
    amount DECIMAL(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    description VARCHAR(255),
    status VARCHAR(30) NOT NULL DEFAULT 'INITIATED',
    risk_score DOUBLE PRECISION,
    initiated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE,
    failed_at TIMESTAMP WITH TIME ZONE,
    failure_reason VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT chk_transaction_type CHECK (transaction_type IN (
        'INTERNAL_TRANSFER', 'EXTERNAL_TRANSFER', 'DEPOSIT', 'WITHDRAWAL',
        'PAYMENT', 'LOAN_DISBURSEMENT', 'LOAN_REPAYMENT', 'INTEREST_CREDIT', 'FEE_DEDUCTION'
    )),
    CONSTRAINT chk_transaction_status CHECK (status IN (
        'INITIATED', 'PENDING_FRAUD_CHECK', 'PENDING_REVIEW', 'PROCESSING',
        'COMPLETED', 'FAILED', 'REVERSED', 'CANCELLED'
    )),
    CONSTRAINT chk_amount_positive CHECK (amount > 0)
);

-- Indexes for transactions
CREATE INDEX idx_transactions_source_account ON transactions(source_account_id);
CREATE INDEX idx_transactions_target_account ON transactions(target_account_id);
CREATE INDEX idx_transactions_status ON transactions(status);
CREATE INDEX idx_transactions_initiated_at ON transactions(initiated_at DESC);
CREATE INDEX idx_transactions_reference ON transactions(reference_number);

-- ============================================
-- EVENT STORE TABLE (for Event Sourcing)
-- ============================================
CREATE TABLE event_store (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    event_data JSONB NOT NULL,
    metadata JSONB,
    version BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_aggregate_version UNIQUE (aggregate_id, version)
);

-- Indexes for event store
CREATE INDEX idx_event_store_aggregate ON event_store(aggregate_type, aggregate_id);
CREATE INDEX idx_event_store_created_at ON event_store(created_at);
CREATE INDEX idx_event_store_event_type ON event_store(event_type);

-- ============================================
-- OUTBOX TABLE (for Transactional Outbox Pattern)
-- ============================================
CREATE TABLE outbox_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP WITH TIME ZONE,
    retry_count INT NOT NULL DEFAULT 0,

    CONSTRAINT chk_outbox_status CHECK (status IN ('PENDING', 'PROCESSED', 'FAILED'))
);

-- Index for outbox processing
CREATE INDEX idx_outbox_status_created ON outbox_events(status, created_at)
    WHERE status = 'PENDING';

-- ============================================
-- LOANS TABLE
-- ============================================
CREATE TABLE loans (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    loan_number VARCHAR(30) NOT NULL UNIQUE,
    account_id UUID NOT NULL REFERENCES accounts(id),
    loan_type VARCHAR(30) NOT NULL,
    principal_amount DECIMAL(19, 4) NOT NULL,
    interest_rate DECIMAL(5, 4) NOT NULL,
    term_months INT NOT NULL,
    monthly_payment DECIMAL(19, 4) NOT NULL,
    outstanding_balance DECIMAL(19, 4) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    disbursed_at TIMESTAMP WITH TIME ZONE,
    maturity_date DATE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT chk_loan_type CHECK (loan_type IN (
        'PERSONAL', 'HOME_EQUITY', 'AUTO', 'BUSINESS', 'MORTGAGE'
    )),
    CONSTRAINT chk_loan_status CHECK (status IN (
        'PENDING', 'APPROVED', 'DISBURSED', 'ACTIVE', 'PAID_OFF', 'DEFAULTED', 'CANCELLED'
    ))
);

-- Indexes for loans
CREATE INDEX idx_loans_account ON loans(account_id);
CREATE INDEX idx_loans_status ON loans(status);

-- ============================================
-- TRIGGERS
-- ============================================

-- Update timestamp trigger function
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Apply to accounts
CREATE TRIGGER update_accounts_updated_at
    BEFORE UPDATE ON accounts
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Apply to loans
CREATE TRIGGER update_loans_updated_at
    BEFORE UPDATE ON loans
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- ============================================
-- COMMENTS
-- ============================================
COMMENT ON TABLE accounts IS 'Bank accounts with balance and status tracking';
COMMENT ON TABLE transactions IS 'Financial transactions between accounts';
COMMENT ON TABLE event_store IS 'Event sourcing store for aggregate events';
COMMENT ON TABLE outbox_events IS 'Transactional outbox for reliable event publishing';
COMMENT ON TABLE loans IS 'Loan products and balances';
