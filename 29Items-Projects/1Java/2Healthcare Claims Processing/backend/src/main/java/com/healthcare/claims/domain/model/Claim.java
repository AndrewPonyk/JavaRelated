package com.healthcare.claims.domain.model;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Core domain entity representing a healthcare insurance claim.
 */
@Entity
@Table(name = "claims", indexes = {
    @Index(name = "idx_claims_status", columnList = "status"),
    @Index(name = "idx_claims_patient_id", columnList = "patient_id"),
    @Index(name = "idx_claims_provider_id", columnList = "provider_id"),
    @Index(name = "idx_claims_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Claim extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "claim_number", unique = true, nullable = false, length = 30)
    private String claimNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClaimType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ClaimStatus status = ClaimStatus.SUBMITTED;

    @NotNull
    @DecimalMin(value = "0.01", message = "Claim amount must be positive")
    @DecimalMax(value = "999999999.99", message = "Claim amount exceeds maximum")
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "allowed_amount", precision = 12, scale = 2)
    private BigDecimal allowedAmount;

    @NotNull
    @PastOrPresent(message = "Service date cannot be in the future")
    @Column(name = "service_date", nullable = false)
    private LocalDate serviceDate;

    @Column(name = "service_end_date")
    private LocalDate serviceEndDate;

    @NotNull
    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", insertable = false, updatable = false)
    private Patient patient;

    @NotNull
    @Column(name = "provider_id", nullable = false)
    private UUID providerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", insertable = false, updatable = false)
    private Provider provider;

    @Column(name = "diagnosis_codes", length = 500)
    private String diagnosisCodes;

    @Column(name = "procedure_codes", length = 500)
    private String procedureCodes;

    @Column(name = "fraud_score")
    private Double fraudScore;

    @Column(name = "fraud_reasons", length = 1000)
    private String fraudReasons;

    @Column(name = "denial_reason", length = 500)
    private String denialReason;

    @Column(length = 2000)
    private String notes;

    @Column(name = "submitted_by")
    private String submittedBy;

    @Column(name = "reviewed_by")
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @OneToMany(mappedBy = "claim", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<AdjudicationResult> adjudicationResults = new ArrayList<>();

    @Version
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Transitions the claim to a new status with validation.
     */
    public void transitionTo(ClaimStatus newStatus) {
        if (!status.canTransitionTo(newStatus)) {
            throw new IllegalStateException(
                String.format("Cannot transition claim from %s to %s", status, newStatus)
            );
        }
        this.status = newStatus;
    }

    /**
     * Checks if the claim is flagged for potential fraud.
     */
    public boolean isFraudFlagged() {
        return fraudScore != null && fraudScore >= 0.7;
    }

    /**
     * Checks if the claim can be auto-adjudicated.
     */
    public boolean isEligibleForAutoAdjudication() {
        return !isFraudFlagged() &&
               amount.compareTo(new BigDecimal("500")) <= 0 &&
               !type.requiresPreAuthorization();
    }

    /**
     * Adds an adjudication result to this claim.
     */
    public void addAdjudicationResult(AdjudicationResult result) {
        adjudicationResults.add(result);
        result.setClaim(this);
    }

    /**
     * Generates a unique claim number.
     */
    @PrePersist
    public void generateClaimNumber() {
        if (claimNumber == null) {
            claimNumber = "CLM-" + System.currentTimeMillis() + "-" +
                         UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        }
    }
}
