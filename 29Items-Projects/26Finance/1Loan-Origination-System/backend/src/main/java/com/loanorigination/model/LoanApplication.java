package com.loanorigination.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "loan_application")
@Data
public class LoanApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "loan_app_seq")
    @SequenceGenerator(name = "loan_app_seq", sequenceName = "loan_app_seq", allocationSize = 1)
    private Long id;

    @Column(name = "application_id", unique = true, nullable = false)
    private String applicationId;

    @Column(name = "loan_amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal loanAmount;

    @Column(name = "loan_purpose")
    private String loanPurpose;

    @Column(name = "loan_term_months")
    private Integer loanTermMonths;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ApplicationStatus status;

    @Column(name = "applicant_id", nullable = false)
    private Long applicantId;

    @Column(name = "credit_score")
    private Integer creditScore;

    @Column(name = "debt_to_income_ratio", precision = 5, scale = 2)
    private BigDecimal debtToIncomeRatio;

    @Column(name = "loan_to_value_ratio", precision = 5, scale = 2)
    private BigDecimal loanToValueRatio;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "underwriting_decision")
    private String underwritingDecision;

    @Column(name = "decision_date")
    private LocalDateTime decisionDate;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) {
            status = ApplicationStatus.SUBMITTED;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum ApplicationStatus {
        SUBMITTED,
        UNDER_REVIEW,
        APPROVED,
        REJECTED,
        MANUAL_REVIEW_REQUIRED,
        FUNDED
    }
}
