package com.healthcare.claims.domain.model;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a healthcare provider (doctor, hospital, clinic, etc.).
 */
@Entity
@Table(name = "providers", indexes = {
    @Index(name = "idx_providers_npi", columnList = "npi"),
    @Index(name = "idx_providers_tax_id", columnList = "tax_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Provider extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotBlank
    @Pattern(regexp = "^\\d{10}$", message = "NPI must be exactly 10 digits")
    @Column(unique = true, nullable = false, length = 10)
    private String npi;

    @NotBlank
    @Size(max = 200)
    @Column(nullable = false, length = 200)
    private String name;

    @Size(max = 100)
    @Column(length = 100)
    private String specialty;

    @Column(name = "tax_id", length = 20)
    private String taxId;

    @Column(name = "in_network")
    @Builder.Default
    private Boolean inNetwork = true;

    @Column(name = "provider_type", length = 50)
    private String providerType;

    @Email
    @Column(length = 255)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(length = 500)
    private String address;

    @Column(length = 100)
    private String city;

    @Column(length = 50)
    private String state;

    @Column(name = "zip_code", length = 10)
    private String zipCode;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "credentialing_status", length = 20)
    @Builder.Default
    private String credentialingStatus = "ACTIVE";

    @Column(name = "fraud_risk_score")
    private Double fraudRiskScore;

    @Version
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Checks if the provider is eligible to submit claims.
     */
    public boolean isEligibleForClaims() {
        return isActive &&
               "ACTIVE".equals(credentialingStatus) &&
               (fraudRiskScore == null || fraudRiskScore < 0.5);
    }

    /**
     * Checks if the provider is flagged for potential fraud.
     */
    public boolean isFraudFlagged() {
        return fraudRiskScore != null && fraudRiskScore >= 0.7;
    }

    /**
     * Gets the network status description.
     */
    public String getNetworkStatusDescription() {
        return inNetwork ? "In-Network" : "Out-of-Network";
    }
}
