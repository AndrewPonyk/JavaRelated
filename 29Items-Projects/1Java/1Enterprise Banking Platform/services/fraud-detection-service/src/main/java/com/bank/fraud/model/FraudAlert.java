package com.bank.fraud.model;

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
 * Entity representing a fraud alert.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("fraud_alerts")
public class FraudAlert {

    @Id
    private UUID id;

    @Column("transaction_id")
    private UUID transactionId;

    @Column("account_id")
    private UUID accountId;

    @Column("amount")
    private BigDecimal amount;

    @Column("currency")
    private String currency;

    @Column("risk_score")
    private double riskScore;

    @Column("risk_level")
    private RiskLevel riskLevel;

    @Column("risk_factors")
    private String riskFactors;

    @Column("status")
    private AlertStatus status;

    @Column("reviewed_by")
    private String reviewedBy;

    @Column("review_notes")
    private String reviewNotes;

    @Column("created_at")
    private Instant createdAt;

    @Column("reviewed_at")
    private Instant reviewedAt;

    @Version
    private Long version;

    public enum RiskLevel {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    public enum AlertStatus {
        PENDING,
        CONFIRMED_FRAUD,
        FALSE_POSITIVE,
        ESCALATED,
        RESOLVED
    }
}
