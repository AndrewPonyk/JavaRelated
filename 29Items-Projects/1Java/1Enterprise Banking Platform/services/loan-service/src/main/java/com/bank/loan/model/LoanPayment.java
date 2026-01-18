package com.bank.loan.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Entity representing a loan payment.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("loan_payments")
public class LoanPayment {

    @Id
    private UUID id;

    @Column("loan_id")
    private UUID loanId;

    @Column("payment_number")
    private int paymentNumber;

    @Column("due_date")
    private LocalDate dueDate;

    @Column("payment_date")
    private Instant paymentDate;

    @Column("principal_amount")
    private BigDecimal principalAmount;

    @Column("interest_amount")
    private BigDecimal interestAmount;

    @Column("total_amount")
    private BigDecimal totalAmount;

    @Column("principal_paid")
    private BigDecimal principalPaid;

    @Column("interest_paid")
    private BigDecimal interestPaid;

    @Column("late_fee")
    private BigDecimal lateFee;

    @Column("status")
    private PaymentStatus status;

    @Column("payment_method")
    private String paymentMethod;

    @Column("transaction_reference")
    private String transactionReference;

    @Column("created_at")
    private Instant createdAt;

    public enum PaymentStatus {
        SCHEDULED,
        PENDING,
        PAID,
        PARTIAL,
        LATE,
        MISSED,
        WAIVED
    }
}
