-- V4__create_adjudication_results_table.sql
-- Healthcare Claims Processing - Adjudication Results Table

CREATE TABLE adjudication_results (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    claim_id UUID NOT NULL REFERENCES claims(id) ON DELETE CASCADE,
    rule_applied VARCHAR(100) NOT NULL,
    rule_version VARCHAR(20),
    decision VARCHAR(20) NOT NULL,
    allowed_amount DECIMAL(12, 2),
    copay_amount DECIMAL(12, 2),
    deductible_amount DECIMAL(12, 2),
    coinsurance_amount DECIMAL(12, 2),
    patient_responsibility DECIMAL(12, 2),
    reason VARCHAR(500),
    denial_code VARCHAR(10),
    remark_codes VARCHAR(100),
    is_automated BOOLEAN DEFAULT TRUE,
    processed_by VARCHAR(100),
    processed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_decision CHECK (decision IN (
        'APPROVED', 'DENIED', 'PENDING_REVIEW', 'FLAGGED', 'PAID'
    ))
);

-- Indexes
CREATE INDEX idx_adjudication_claim_id ON adjudication_results(claim_id);
CREATE INDEX idx_adjudication_processed_at ON adjudication_results(processed_at DESC);
CREATE INDEX idx_adjudication_decision ON adjudication_results(decision);
CREATE INDEX idx_adjudication_rule ON adjudication_results(rule_applied);

COMMENT ON TABLE adjudication_results IS 'Results of claim adjudication rule evaluations';
COMMENT ON COLUMN adjudication_results.rule_applied IS 'Name of the rule that produced this result';
COMMENT ON COLUMN adjudication_results.denial_code IS 'Standard denial code (e.g., CARC/RARC)';
