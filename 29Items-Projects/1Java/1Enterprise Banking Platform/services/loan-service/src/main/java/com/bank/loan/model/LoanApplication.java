package com.bank.loan.model;

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
import java.time.LocalDate;
import java.util.UUID;

/**
 * Entity representing a loan application.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Table("loan_applications")
public class LoanApplication {

    @Id
    private UUID id;

    @Column("application_number")
    private String applicationNumber;

    @Column("applicant_id")
    private UUID applicantId;

    @Column("applicant_name")
    private String applicantName;

    @Column("applicant_email")
    private String applicantEmail;

    @Column("loan_type")
    private LoanType loanType;

    @Column("requested_amount")
    private BigDecimal requestedAmount;

    @Column("approved_amount")
    private BigDecimal approvedAmount;

    @Column("currency")
    private String currency;

    @Column("term_months")
    private int termMonths;

    @Column("interest_rate")
    private BigDecimal interestRate;

    @Column("monthly_payment")
    private BigDecimal monthlyPayment;

    @Column("status")
    private ApplicationStatus status;

    @Column("credit_score")
    private Integer creditScore;

    @Column("annual_income")
    private BigDecimal annualIncome;

    @Column("employment_status")
    private EmploymentStatus employmentStatus;

    @Column("purpose")
    private String purpose;

    @Column("collateral_description")
    private String collateralDescription;

    @Column("collateral_value")
    private BigDecimal collateralValue;

    @Column("rejection_reason")
    private String rejectionReason;

    @Column("reviewed_by")
    private String reviewedBy;

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;

    @Column("approved_at")
    private Instant approvedAt;

    @Column("disbursed_at")
    private Instant disbursedAt;

    @Version
    private Long version;

    public enum LoanType {
        PERSONAL,
        MORTGAGE,
        AUTO,
        BUSINESS,
        EDUCATION,
        HOME_EQUITY
    }

    public enum ApplicationStatus {
        DRAFT,
        SUBMITTED,
        UNDER_REVIEW,
        DOCUMENTS_REQUIRED,
        APPROVED,
        REJECTED,
        DISBURSED,
        CANCELLED,
        CLOSED
    }

    public enum EmploymentStatus {
        EMPLOYED,
        SELF_EMPLOYED,
        UNEMPLOYED,
        RETIRED,
        STUDENT
    }
}
