-- Loan Application Tables
CREATE TABLE loan_application (
    id NUMBER(19) PRIMARY KEY,
    application_id VARCHAR2(50) UNIQUE NOT NULL,
    loan_amount NUMBER(15,2) NOT NULL,
    loan_purpose VARCHAR2(100),
    loan_term_months NUMBER(3),
    status VARCHAR2(30) NOT NULL,
    applicant_id NUMBER(19) NOT NULL,
    credit_score NUMBER(3),
    debt_to_income_ratio NUMBER(5,2),
    loan_to_value_ratio NUMBER(5,2),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    underwriting_decision VARCHAR2(30),
    decision_date TIMESTAMP
);

CREATE SEQUENCE loan_app_seq START WITH 1 INCREMENT BY 1;

CREATE INDEX idx_loan_app_status ON loan_application(status);
CREATE INDEX idx_loan_app_applicant ON loan_application(applicant_id);
CREATE INDEX idx_loan_app_created ON loan_application(created_at);

-- Applicant Table
CREATE TABLE applicant (
    id NUMBER(19) PRIMARY KEY,
    first_name VARCHAR2(100) NOT NULL,
    last_name VARCHAR2(100) NOT NULL,
    email VARCHAR2(255) UNIQUE NOT NULL,
    phone VARCHAR2(20),
    date_of_birth DATE,
    ssn VARCHAR2(11),
    annual_income NUMBER(15,2),
    employment_status VARCHAR2(50),
    employer_name VARCHAR2(255),
    years_employed NUMBER(3,1),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);

CREATE SEQUENCE applicant_seq START WITH 1 INCREMENT BY 1;

-- No explicit index on email — the UNIQUE constraint already creates one.

-- Underwriting Decision Table
CREATE TABLE underwriting_decision (
    id NUMBER(19) PRIMARY KEY,
    application_id NUMBER(19) NOT NULL,
    decision VARCHAR2(30) NOT NULL,
    decision_reason VARCHAR2(500),
    credit_score NUMBER(3),
    risk_score NUMBER(5,4),
    automated CHAR(1) DEFAULT 'Y',
    underwriter_id NUMBER(19),
    decision_date TIMESTAMP NOT NULL,
    conditions CLOB,
    CONSTRAINT fk_uw_application FOREIGN KEY (application_id) 
        REFERENCES loan_application(id)
);

CREATE SEQUENCE underwriting_decision_seq START WITH 1 INCREMENT BY 1;

CREATE INDEX idx_uw_application ON underwriting_decision(application_id);
CREATE INDEX idx_uw_decision_date ON underwriting_decision(decision_date);

-- Document Table
CREATE TABLE loan_document (
    id NUMBER(19) PRIMARY KEY,
    application_id NUMBER(19) NOT NULL,
    document_type VARCHAR2(50) NOT NULL,
    document_name VARCHAR2(255) NOT NULL,
    s3_key VARCHAR2(500) NOT NULL,
    s3_bucket VARCHAR2(255) NOT NULL,
    file_size NUMBER(19),
    mime_type VARCHAR2(100),
    uploaded_by NUMBER(19),
    uploaded_at TIMESTAMP NOT NULL,
    processed CHAR(1) DEFAULT 'N',
    ocr_text CLOB,
    CONSTRAINT fk_doc_application FOREIGN KEY (application_id) 
        REFERENCES loan_application(id)
);

CREATE SEQUENCE loan_document_seq START WITH 1 INCREMENT BY 1;

CREATE INDEX idx_doc_application ON loan_document(application_id);
CREATE INDEX idx_doc_type ON loan_document(document_type);

-- Audit Log Table
CREATE TABLE audit_log (
    id NUMBER(19) PRIMARY KEY,
    entity_type VARCHAR2(50) NOT NULL,
    entity_id NUMBER(19) NOT NULL,
    action VARCHAR2(50) NOT NULL,
    user_id NUMBER(19),
    user_email VARCHAR2(255),
    timestamp TIMESTAMP NOT NULL,
    old_values CLOB,
    new_values CLOB,
    ip_address VARCHAR2(45)
);

CREATE SEQUENCE audit_log_seq START WITH 1 INCREMENT BY 1;

CREATE INDEX idx_audit_entity ON audit_log(entity_type, entity_id);
CREATE INDEX idx_audit_timestamp ON audit_log(timestamp);

COMMENT ON TABLE loan_application IS 'Stores loan application submissions';
COMMENT ON TABLE applicant IS 'Stores applicant personal and financial information';
COMMENT ON TABLE underwriting_decision IS 'Stores underwriting decisions (automated and manual)';
COMMENT ON TABLE loan_document IS 'Stores document metadata with S3 references';
COMMENT ON TABLE audit_log IS 'Audit trail for all sensitive operations';
