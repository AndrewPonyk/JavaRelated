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
 * Entity representing an active loan.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("loans")
public class Loan {

    @Id
    private UUID id;

    @Column("loan_number")
    private String loanNumber;

    @Column("application_id")
    private UUID applicationId;

    @Column("borrower_id")
    private UUID borrowerId;

    @Column("borrower_name")
    private String borrowerName;

    @Column("disbursement_account_id")
    private UUID disbursementAccountId;

    @Column("loan_type")
    private LoanApplication.LoanType loanType;

    @Column("principal_amount")
    private BigDecimal principalAmount;

    @Column("currency")
    private String currency;

    @Column("interest_rate")
    private BigDecimal interestRate;

    @Column("term_months")
    private int termMonths;

    @Column("monthly_payment")
    private BigDecimal monthlyPayment;

    @Column("outstanding_principal")
    private BigDecimal outstandingPrincipal;

    @Column("outstanding_interest")
    private BigDecimal outstandingInterest;

    @Column("total_paid")
    private BigDecimal totalPaid;

    @Column("next_payment_due")
    private LocalDate nextPaymentDue;

    @Column("status")
    private LoanStatus status;

    @Column("start_date")
    private LocalDate startDate;

    @Column("end_date")
    private LocalDate endDate;

    @Column("payments_made")
    private int paymentsMade;

    @Column("payments_missed")
    private int paymentsMissed;

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;

    @Version
    private Long version;

    public enum LoanStatus {
        ACTIVE,
        DELINQUENT,
        DEFAULT,
        PAID_OFF,
        CLOSED,
        WRITTEN_OFF
    }

    /**
     * Record a payment.
     */
    public void recordPayment(BigDecimal principalPaid, BigDecimal interestPaid) {
        this.outstandingPrincipal = this.outstandingPrincipal.subtract(principalPaid);
        this.outstandingInterest = this.outstandingInterest.subtract(interestPaid);
        this.totalPaid = this.totalPaid.add(principalPaid).add(interestPaid);
        this.paymentsMade++;
        this.nextPaymentDue = this.nextPaymentDue.plusMonths(1);
        this.updatedAt = Instant.now();

        if (this.outstandingPrincipal.compareTo(BigDecimal.ZERO) <= 0) {
            this.status = LoanStatus.PAID_OFF;
        }
    }

    /**
     * Mark a missed payment.
     */
    public void missPayment() {
        this.paymentsMissed++;
        this.updatedAt = Instant.now();

        if (this.paymentsMissed >= 3) {
            this.status = LoanStatus.DEFAULT;
        } else if (this.paymentsMissed >= 1) {
            this.status = LoanStatus.DELINQUENT;
        }
    }
}
