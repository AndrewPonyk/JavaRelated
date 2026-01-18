package com.bank.transaction.model;

/**
 * Transaction lifecycle status.
 */
public enum TransactionStatus {
    INITIATED,           // Transaction created
    PENDING_FRAUD_CHECK, // Awaiting fraud check
    PENDING_REVIEW,      // Flagged for manual review
    PROCESSING,          // Being processed
    COMPLETED,           // Successfully completed
    FAILED,              // Transaction failed
    REVERSED,            // Transaction reversed
    CANCELLED            // Cancelled by user
}
