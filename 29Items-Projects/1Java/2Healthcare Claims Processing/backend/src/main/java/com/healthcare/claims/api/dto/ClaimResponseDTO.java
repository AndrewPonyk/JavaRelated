package com.healthcare.claims.api.dto;

import com.healthcare.claims.domain.model.Claim;
import com.healthcare.claims.domain.model.ClaimStatus;
import com.healthcare.claims.domain.model.ClaimType;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for claim responses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClaimResponseDTO {

    private UUID id;
    private String claimNumber;
    private ClaimType type;
    private ClaimStatus status;
    private BigDecimal amount;
    private BigDecimal allowedAmount;
    private LocalDate serviceDate;
    private LocalDate serviceEndDate;
    private UUID patientId;
    private UUID providerId;
    private String diagnosisCodes;
    private String procedureCodes;
    private Double fraudScore;
    private String fraudReasons;
    private String denialReason;
    private String notes;
    private String submittedBy;
    private String reviewedBy;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Creates a DTO from a Claim entity.
     */
    public static ClaimResponseDTO fromEntity(Claim claim) {
        if (claim == null) return null;

        return ClaimResponseDTO.builder()
            .id(claim.getId())
            .claimNumber(claim.getClaimNumber())
            .type(claim.getType())
            .status(claim.getStatus())
            .amount(claim.getAmount())
            .allowedAmount(claim.getAllowedAmount())
            .serviceDate(claim.getServiceDate())
            .serviceEndDate(claim.getServiceEndDate())
            .patientId(claim.getPatientId())
            .providerId(claim.getProviderId())
            .diagnosisCodes(claim.getDiagnosisCodes())
            .procedureCodes(claim.getProcedureCodes())
            .fraudScore(claim.getFraudScore())
            .fraudReasons(claim.getFraudReasons())
            .denialReason(claim.getDenialReason())
            .notes(claim.getNotes())
            .submittedBy(claim.getSubmittedBy())
            .reviewedBy(claim.getReviewedBy())
            .reviewedAt(claim.getReviewedAt())
            .createdAt(claim.getCreatedAt())
            .updatedAt(claim.getUpdatedAt())
            .build();
    }

    /**
     * Checks if the claim requires action.
     */
    public boolean requiresAction() {
        return status == ClaimStatus.PENDING_REVIEW ||
               status == ClaimStatus.FLAGGED_FRAUD;
    }
}
