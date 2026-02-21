package com.loanorigination.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class LoanApplicationDto {

    private Long id;

    private String applicationId;

    @NotNull(message = "Loan amount is required")
    @DecimalMin(value = "1000.00", message = "Loan amount must be at least $1,000")
    @DecimalMax(value = "1000000.00", message = "Loan amount cannot exceed $1,000,000")
    private BigDecimal loanAmount;

    @NotBlank(message = "Loan purpose is required")
    private String loanPurpose;

    @Min(value = 12, message = "Loan term must be at least 12 months")
    @Max(value = 360, message = "Loan term cannot exceed 360 months")
    private Integer loanTermMonths;

    @NotNull(message = "Applicant ID is required")
    private Long applicantId;

    private String status;

    private Integer creditScore;

    private BigDecimal debtToIncomeRatio;

    private BigDecimal loanToValueRatio;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private String underwritingDecision;

    private LocalDateTime decisionDate;
}
