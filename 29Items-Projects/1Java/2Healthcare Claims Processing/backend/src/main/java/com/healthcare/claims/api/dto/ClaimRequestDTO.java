package com.healthcare.claims.api.dto;

import com.healthcare.claims.domain.model.ClaimType;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO for claim submission requests.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClaimRequestDTO {

    @NotNull(message = "Claim type is required")
    private ClaimType type;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be positive")
    @DecimalMax(value = "999999999.99", message = "Amount exceeds maximum")
    private BigDecimal amount;

    @NotNull(message = "Service date is required")
    @PastOrPresent(message = "Service date cannot be in the future")
    private LocalDate serviceDate;

    private LocalDate serviceEndDate;

    @NotNull(message = "Patient ID is required")
    private UUID patientId;

    @NotNull(message = "Provider ID is required")
    private UUID providerId;

    @Size(max = 500, message = "Diagnosis codes exceed maximum length")
    private String diagnosisCodes;

    @Size(max = 500, message = "Procedure codes exceed maximum length")
    private String procedureCodes;

    @Size(max = 2000, message = "Notes exceed maximum length")
    private String notes;
}
