package com.healthcare.claims.domain.model;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents the result of applying an adjudication rule to a claim.
 */
@Entity
@Table(name = "adjudication_results", indexes = {
    @Index(name = "idx_adjudication_claim_id", columnList = "claim_id"),
    @Index(name = "idx_adjudication_processed_at", columnList = "processed_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdjudicationResult extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "claim_id", nullable = false)
    private Claim claim;

    @NotBlank
    @Column(name = "rule_applied", nullable = false, length = 100)
    private String ruleApplied;

    @NotBlank
    @Column(name = "rule_version", length = 20)
    private String ruleVersion;

    @NotBlank
    @Column(nullable = false, length = 20)
    private String decision;

    @Column(name = "allowed_amount", precision = 12, scale = 2)
    private BigDecimal allowedAmount;

    @Column(name = "copay_amount", precision = 12, scale = 2)
    private BigDecimal copayAmount;

    @Column(name = "deductible_amount", precision = 12, scale = 2)
    private BigDecimal deductibleAmount;

    @Column(name = "coinsurance_amount", precision = 12, scale = 2)
    private BigDecimal coinsuranceAmount;

    @Column(name = "patient_responsibility", precision = 12, scale = 2)
    private BigDecimal patientResponsibility;

    @Column(length = 500)
    private String reason;

    @Column(name = "denial_code", length = 10)
    private String denialCode;

    @Column(name = "remark_codes", length = 100)
    private String remarkCodes;

    @Column(name = "is_automated")
    @Builder.Default
    private Boolean isAutomated = true;

    @Column(name = "processed_by", length = 100)
    private String processedBy;

    @CreationTimestamp
    @Column(name = "processed_at", nullable = false, updatable = false)
    private LocalDateTime processedAt;

    /**
     * Checks if the result is an approval.
     */
    public boolean isApproved() {
        return "APPROVED".equalsIgnoreCase(decision) || "PAID".equalsIgnoreCase(decision);
    }

    /**
     * Checks if the result is a denial.
     */
    public boolean isDenied() {
        return "DENIED".equalsIgnoreCase(decision);
    }

    /**
     * Checks if the result requires further review.
     */
    public boolean requiresReview() {
        return "PENDING_REVIEW".equalsIgnoreCase(decision) ||
               "FLAGGED".equalsIgnoreCase(decision);
    }

    /**
     * Calculates total patient out-of-pocket amount.
     */
    public BigDecimal getTotalPatientOutOfPocket() {
        BigDecimal total = BigDecimal.ZERO;
        if (copayAmount != null) total = total.add(copayAmount);
        if (deductibleAmount != null) total = total.add(deductibleAmount);
        if (coinsuranceAmount != null) total = total.add(coinsuranceAmount);
        return total;
    }
}
