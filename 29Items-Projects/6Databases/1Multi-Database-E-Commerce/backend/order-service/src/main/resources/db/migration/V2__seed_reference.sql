-- ============================================================================
-- V2: transactional OUTBOX table for reliable event publishing.
-- Events are inserted in the SAME transaction as the order write; a relay
-- (poller / Debezium CDC) ships PENDING rows to Kafka and marks them SENT.
-- This is the production-grade guarantee behind OrderEventPublisher.
-- ============================================================================

CREATE TABLE OUTBOX_EVENTS (
    ID            VARCHAR2(36)   NOT NULL,
    AGGREGATE_ID  VARCHAR2(64)   NOT NULL,
    TYPE          VARCHAR2(64)   NOT NULL,
    TOPIC         VARCHAR2(128)  NOT NULL,
    PAYLOAD       CLOB           NOT NULL,
    STATUS        VARCHAR2(16)   DEFAULT 'PENDING' NOT NULL,
    CREATED_AT    TIMESTAMP(6)   DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT PK_OUTBOX PRIMARY KEY (ID),
    CONSTRAINT CK_OUTBOX_STATUS CHECK (STATUS IN ('PENDING','SENT','FAILED'))
);

-- Relay scans PENDING rows oldest-first.
CREATE INDEX IX_OUTBOX_PENDING ON OUTBOX_EVENTS (STATUS, CREATED_AT);
