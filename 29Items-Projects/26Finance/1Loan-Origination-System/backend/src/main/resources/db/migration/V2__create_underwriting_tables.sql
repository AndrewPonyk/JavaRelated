-- Add indexes and additional tables for underwriting
CREATE INDEX idx_loan_app_credit_score ON loan_application(credit_score);
CREATE INDEX idx_loan_app_dti ON loan_application(debt_to_income_ratio);
CREATE INDEX idx_loan_app_decision_date ON loan_application(decision_date);

-- Event store table for event sourcing
CREATE TABLE event_store (
    id NUMBER(19) PRIMARY KEY,
    aggregate_id VARCHAR2(100) NOT NULL,
    aggregate_type VARCHAR2(50) NOT NULL,
    event_type VARCHAR2(100) NOT NULL,
    event_data CLOB NOT NULL,
    event_timestamp TIMESTAMP NOT NULL,
    version NUMBER(10) NOT NULL,
    CONSTRAINT uk_event_version UNIQUE (aggregate_id, version)
);

CREATE SEQUENCE event_store_seq START WITH 1 INCREMENT BY 1;

CREATE INDEX idx_event_aggregate ON event_store(aggregate_id, aggregate_type);
CREATE INDEX idx_event_timestamp ON event_store(event_timestamp);
CREATE INDEX idx_event_type ON event_store(event_type);

COMMENT ON TABLE event_store IS 'Event sourcing store for all domain events';
