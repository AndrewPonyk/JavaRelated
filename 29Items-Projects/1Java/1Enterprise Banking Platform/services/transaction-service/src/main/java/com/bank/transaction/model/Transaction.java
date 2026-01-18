package com.bank.transaction.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Transaction domain entity.
 * Represents a fund transfer or payment transaction.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Table("transactions")
public class Transaction {

    @Id
    private UUID id;

    @Column("reference_number")
    private String referenceNumber;

    @Column("source_account_id")
    private UUID sourceAccountId;

    @Column("target_account_id")
    private UUID targetAccountId;

    @Column("transaction_type")
    private TransactionType transactionType;

    @Column("amount")
    private BigDecimal amount;

    @Column("currency")
    private String currency;

    @Column("description")
    private String description;

    @Column("status")
    private TransactionStatus status;

    @Column("risk_score")
    private Double riskScore;

    @Column("initiated_at")
    private Instant initiatedAt;

    @Column("completed_at")
    private Instant completedAt;

    @Column("failed_at")
    private Instant failedAt;

    @Column("failure_reason")
    private String failureReason;

    @Version
    private Long version;

    /**
     * Mark the transaction as completed.
     */
    public void complete() {
        this.status = TransactionStatus.COMPLETED;
        this.completedAt = Instant.now();
    }

    /**
     * Mark the transaction as failed.
     */
    public void fail(String reason) {
        this.status = TransactionStatus.FAILED;
        this.failedAt = Instant.now();
        this.failureReason = reason;
    }

    /**
     * Mark the transaction as pending review.
     */
    public void flagForReview() {
        this.status = TransactionStatus.PENDING_REVIEW;
    }
}
