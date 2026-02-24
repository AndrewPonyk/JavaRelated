-- Add credit history fields to applicant table (previously hardcoded in UnderwritingService)
ALTER TABLE applicant ADD existing_debt NUMBER(15,2);
ALTER TABLE applicant ADD num_previous_loans NUMBER(3);
ALTER TABLE applicant ADD num_delinquencies NUMBER(3);
