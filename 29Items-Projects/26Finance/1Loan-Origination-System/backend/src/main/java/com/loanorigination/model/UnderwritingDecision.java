package com.loanorigination.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "underwriting_decision")
@Data
public class UnderwritingDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "underwriting_decision_seq")
    @SequenceGenerator(name = "underwriting_decision_seq", sequenceName = "underwriting_decision_seq", allocationSize = 1)
    private Long id;

    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, length = 30)
    private Decision decision;

    @Column(name = "decision_reason", length = 500)
    private String decisionReason;

    @Column(name = "credit_score")
    private Integer creditScore;

    @Column(name = "risk_score", precision = 5, scale = 4)
    private BigDecimal riskScore;

    /**
     * Stored as 'Y' / 'N' in the AUTOMATED column (CHAR(1)).
     * Lombok generates isAutomated() / setAutomated() automatically via @Data.
     */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "automated", length = 1)
    private boolean automated = true;

    @Column(name = "underwriter_id")
    private Long underwriterId;

    @Column(name = "decision_date", nullable = false)
    private LocalDateTime decisionDate;

    @Column(name = "conditions", columnDefinition = "CLOB")
    private String conditions;

    @PrePersist
    protected void onCreate() {
        if (decisionDate == null) {
            decisionDate = LocalDateTime.now();
        }
    }

    public enum Decision {
        APPROVED,
        REJECTED,
        MANUAL_REVIEW
    }
}
