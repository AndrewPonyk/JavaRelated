package com.bank.loan.dto;

import com.bank.loan.model.LoanApplication.EmploymentStatus;
import com.bank.loan.model.LoanApplication.LoanType;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request DTO for creating a loan application.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class LoanApplicationRequest {

    @NotNull(message = "Applicant ID is required")
    private UUID applicantId;

    @NotBlank(message = "Applicant name is required")
    private String applicantName;

    @NotBlank(message = "Applicant email is required")
    @Email(message = "Invalid email format")
    private String applicantEmail;

    @NotNull(message = "Loan type is required")
    private LoanType loanType;

    @NotNull(message = "Requested amount is required")
    @DecimalMin(value = "1000", message = "Minimum loan amount is 1000")
    @DecimalMax(value = "10000000", message = "Maximum loan amount is 10,000,000")
    private BigDecimal requestedAmount;

    @NotBlank(message = "Currency is required")
    private String currency;

    @Min(value = 6, message = "Minimum term is 6 months")
    @Max(value = 360, message = "Maximum term is 360 months (30 years)")
    private int termMonths;

    @NotNull(message = "Annual income is required")
    @DecimalMin(value = "0", message = "Annual income cannot be negative")
    private BigDecimal annualIncome;

    @NotNull(message = "Employment status is required")
    private EmploymentStatus employmentStatus;

    private String purpose;

    private String collateralDescription;

    private BigDecimal collateralValue;
}
