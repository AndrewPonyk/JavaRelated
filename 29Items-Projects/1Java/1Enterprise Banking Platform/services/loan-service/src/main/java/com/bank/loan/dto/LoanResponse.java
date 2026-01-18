package com.bank.loan.dto;

import com.bank.loan.model.Loan.LoanStatus;
import com.bank.loan.model.LoanApplication.LoanType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response DTO for loan.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanResponse {

    private UUID id;
    private String loanNumber;
    private UUID applicationId;
    private UUID borrowerId;
    private String borrowerName;
    private LoanType loanType;
    private BigDecimal principalAmount;
    private String currency;
    private BigDecimal interestRate;
    private int termMonths;
    private BigDecimal monthlyPayment;
    private BigDecimal outstandingPrincipal;
    private BigDecimal outstandingInterest;
    private BigDecimal totalPaid;
    private LocalDate nextPaymentDue;
    private LoanStatus status;
    private LocalDate startDate;
    private LocalDate endDate;
    private int paymentsMade;
    private int paymentsMissed;
    private Instant createdAt;
}
