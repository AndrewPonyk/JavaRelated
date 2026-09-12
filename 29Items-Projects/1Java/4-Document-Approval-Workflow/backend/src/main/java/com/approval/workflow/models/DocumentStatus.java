package com.approval.workflow.models;

/**
 * State machine statuses for Document lifecycle.
 */
public enum DocumentStatus {
    DRAFT,
    SUBMITTED,
    IN_REVIEW,
    APPROVED,
    REJECTED,
    REVISION_REQUIRED,
    SLA_BREACHED;

    public boolean isTerminal() {
        return this == APPROVED || this == REJECTED;
    }
}
