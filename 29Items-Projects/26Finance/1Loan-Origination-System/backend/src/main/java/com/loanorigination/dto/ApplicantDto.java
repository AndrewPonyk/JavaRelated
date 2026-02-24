package com.loanorigination.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ApplicantDto {
    private Long id;

    @NotBlank(message = "First name is required")
    private String firstName;

    @NotBlank(message = "Last name is required")
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Invalid phone number")
    private String phone;

    @Past(message = "Date of birth must be in the past")
    private LocalDate dateOfBirth;

    private String ssn;

    @NotNull(message = "Annual income is required")
    @DecimalMin(value = "0.0", message = "Annual income must be positive")
    private BigDecimal annualIncome;

    private String employmentStatus;
    private String employerName;
    private BigDecimal yearsEmployed;

    @DecimalMin(value = "0.0", message = "Existing debt must be positive")
    private BigDecimal existingDebt;

    @Min(value = 0, message = "Number of previous loans must be non-negative")
    private Integer numPreviousLoans;

    @Min(value = 0, message = "Number of delinquencies must be non-negative")
    private Integer numDelinquencies;
}
