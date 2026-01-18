-- Enterprise Banking Platform - Audit Tables
-- Flyway migration V2
-- Adds audit logging and compliance tables

-- ============================================
-- AUDIT LOG TABLE
-- ============================================
CREATE TABLE audit_log (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    entity_type VARCHAR(50) NOT NULL,
    entity_id UUID NOT NULL,
    action VARCHAR(20) NOT NULL,
    actor_id VARCHAR(255),
    actor_type VARCHAR(20) NOT NULL DEFAULT 'USER',
    old_value JSONB,
    new_value JSONB,
    ip_address INET,
    user_agent VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_action CHECK (action IN (
        'CREATE', 'READ', 'UPDATE', 'DELETE', 'LOGIN', 'LOGOUT', 'TRANSFER', 'FREEZE', 'UNFREEZE'
    )),
    CONSTRAINT chk_actor_type CHECK (actor_type IN ('USER', 'SYSTEM', 'ADMIN', 'API'))
);

-- Indexes for audit log
CREATE INDEX idx_audit_entity ON audit_log(entity_type, entity_id);
CREATE INDEX idx_audit_actor ON audit_log(actor_id);
CREATE INDEX idx_audit_created_at ON audit_log(created_at DESC);
CREATE INDEX idx_audit_action ON audit_log(action);

-- Partition by month for better performance
-- (Note: In production, you would create actual partitions)
-- CREATE INDEX idx_audit_created_at_month ON audit_log(date_trunc('month', created_at));

-- ============================================
-- FRAUD ALERTS TABLE
-- ============================================
CREATE TABLE fraud_alerts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    transaction_id UUID REFERENCES transactions(id),
    account_id UUID NOT NULL REFERENCES accounts(id),
    alert_type VARCHAR(50) NOT NULL,
    risk_score DOUBLE PRECISION NOT NULL,
    risk_factors JSONB NOT NULL DEFAULT '[]',
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    assigned_to VARCHAR(255),
    resolution VARCHAR(500),
    resolved_at TIMESTAMP WITH TIME ZONE,
    resolved_by VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_alert_type CHECK (alert_type IN (
        'SUSPICIOUS_TRANSACTION', 'UNUSUAL_ACTIVITY', 'VELOCITY_BREACH',
        'GEO_ANOMALY', 'DEVICE_CHANGE', 'ACCOUNT_TAKEOVER', 'MONEY_LAUNDERING'
    )),
    CONSTRAINT chk_alert_status CHECK (status IN (
        'OPEN', 'INVESTIGATING', 'ESCALATED', 'RESOLVED_FRAUD', 'RESOLVED_FALSE_POSITIVE'
    ))
);

-- Indexes for fraud alerts
CREATE INDEX idx_fraud_alerts_account ON fraud_alerts(account_id);
CREATE INDEX idx_fraud_alerts_status ON fraud_alerts(status);
CREATE INDEX idx_fraud_alerts_created ON fraud_alerts(created_at DESC);
CREATE INDEX idx_fraud_alerts_transaction ON fraud_alerts(transaction_id);

-- ============================================
-- COMPLIANCE REPORTS TABLE
-- ============================================
CREATE TABLE compliance_reports (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    report_type VARCHAR(50) NOT NULL,
    report_period_start DATE NOT NULL,
    report_period_end DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    data JSONB NOT NULL,
    generated_by VARCHAR(255) NOT NULL,
    generated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    submitted_at TIMESTAMP WITH TIME ZONE,
    submitted_by VARCHAR(255),

    CONSTRAINT chk_report_type CHECK (report_type IN (
        'SAR', 'CTR', 'OFAC_SCREENING', 'KYC_REVIEW', 'AML_REPORT', 'REGULATORY_FILING'
    )),
    CONSTRAINT chk_report_status CHECK (status IN (
        'PENDING', 'GENERATED', 'REVIEWED', 'SUBMITTED', 'ARCHIVED'
    ))
);

-- Indexes for compliance reports
CREATE INDEX idx_compliance_reports_type ON compliance_reports(report_type);
CREATE INDEX idx_compliance_reports_period ON compliance_reports(report_period_start, report_period_end);
CREATE INDEX idx_compliance_reports_status ON compliance_reports(status);

-- ============================================
-- USER SESSIONS TABLE (for security tracking)
-- ============================================
CREATE TABLE user_sessions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id VARCHAR(255) NOT NULL,
    session_token VARCHAR(255) NOT NULL UNIQUE,
    device_fingerprint VARCHAR(255),
    ip_address INET,
    user_agent VARCHAR(500),
    location_country VARCHAR(2),
    location_city VARCHAR(100),
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_activity_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    terminated_at TIMESTAMP WITH TIME ZONE,
    termination_reason VARCHAR(50)
);

-- Indexes for user sessions
CREATE INDEX idx_user_sessions_user ON user_sessions(user_id);
CREATE INDEX idx_user_sessions_active ON user_sessions(is_active) WHERE is_active = true;
CREATE INDEX idx_user_sessions_expires ON user_sessions(expires_at);

-- ============================================
-- RATE LIMITING TABLE
-- ============================================
CREATE TABLE rate_limit_buckets (
    id VARCHAR(255) PRIMARY KEY,
    bucket_key VARCHAR(255) NOT NULL,
    tokens_remaining INT NOT NULL,
    last_refill_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Index for rate limiting
CREATE INDEX idx_rate_limit_key ON rate_limit_buckets(bucket_key);

-- ============================================
-- TRIGGER FOR AUDIT LOG TIMESTAMP
-- ============================================
CREATE TRIGGER update_fraud_alerts_updated_at
    BEFORE UPDATE ON fraud_alerts
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- ============================================
-- COMMENTS
-- ============================================
COMMENT ON TABLE audit_log IS 'Comprehensive audit trail for all system actions';
COMMENT ON TABLE fraud_alerts IS 'Fraud detection alerts requiring investigation';
COMMENT ON TABLE compliance_reports IS 'Regulatory compliance reports and filings';
COMMENT ON TABLE user_sessions IS 'Active user sessions for security monitoring';
COMMENT ON TABLE rate_limit_buckets IS 'Token bucket storage for API rate limiting';
