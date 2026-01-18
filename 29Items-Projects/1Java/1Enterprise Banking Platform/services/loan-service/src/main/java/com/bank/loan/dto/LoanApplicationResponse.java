package com.bank.loan.dto;

import com.bank.loan.model.LoanApplication.ApplicationStatus;
import com.bank.loan.model.LoanApplication.EmploymentStatus;
import com.bank.loan.model.LoanApplication.LoanType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for loan application.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanApplicationResponse {

    private UUID id;
    private String applicationNumber;
    private UUID applicantId;
    private String applicantName;
    private String applicantEmail;
    private LoanType loanType;
    private BigDecimal requestedAmount;
    private BigDecimal approvedAmount;
    private String currency;
    private int termMonths;
    private BigDecimal interestRate;
    private BigDecimal monthlyPayment;
    private ApplicationStatus status;
    private Integer creditScore;
    private BigDecimal annualIncome;
    private EmploymentStatus employmentStatus;
    private String purpose;
    private String rejectionReason;
    private String reviewedBy;
    private Instant createdAt;
    private Instant approvedAt;
}
