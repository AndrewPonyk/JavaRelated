-- Enterprise Banking Platform Database Schema
-- Version 2: Performance Optimization Indexes

-- ===========================================
-- Composite Indexes for Common Query Patterns
-- ===========================================

-- Account Service: Owner email lookup with status filter
CREATE INDEX IF NOT EXISTS idx_accounts_owner_email_status
    ON account_service.accounts(owner_email, status);

-- Transaction Service: Account transactions with date range
CREATE INDEX IF NOT EXISTS idx_transactions_source_initiated
    ON transaction_service.transactions(source_account_id, initiated_at DESC);

-- Transaction Service: Pending review transactions ordered by date
CREATE INDEX IF NOT EXISTS idx_transactions_status_initiated
    ON transaction_service.transactions(status, initiated_at ASC)
    WHERE status = 'PENDING_REVIEW';

-- Fraud Service: Pending alerts by risk score (for prioritization)
CREATE INDEX IF NOT EXISTS idx_fraud_alerts_status_risk
    ON fraud_service.fraud_alerts(status, severity DESC)
    WHERE status IN ('PENDING', 'UNDER_INVESTIGATION');

-- Fraud Service: Date range queries for reporting
CREATE INDEX IF NOT EXISTS idx_fraud_alerts_created_status
    ON fraud_service.fraud_alerts(created_at DESC, status);

-- Loan Service: Application status with creation date
CREATE INDEX IF NOT EXISTS idx_loan_applications_status_created
    ON loan_service.loan_applications(status, created_at ASC)
    WHERE status IN ('SUBMITTED', 'UNDER_REVIEW', 'DOCUMENTS_REQUIRED');

-- Loan Service: Loans due for payment
CREATE INDEX IF NOT EXISTS idx_loans_next_payment_status
    ON loan_service.loans(next_payment_date, status)
    WHERE status = 'ACTIVE';

-- Loan Service: Payments due
CREATE INDEX IF NOT EXISTS idx_loan_payments_due_status
    ON loan_service.loan_payments(due_date, status)
    WHERE status IN ('SCHEDULED', 'OVERDUE');
