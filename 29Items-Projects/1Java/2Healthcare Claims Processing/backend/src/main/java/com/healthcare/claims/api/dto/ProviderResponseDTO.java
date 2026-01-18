package com.healthcare.claims.api.dto;

import com.healthcare.claims.domain.model.Provider;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for provider responses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderResponseDTO {

    private UUID id;
    private String npi;
    private String name;
    private String specialty;
    private String taxId;
    private Boolean inNetwork;
    private String networkStatus;
    private String providerType;
    private String email;
    private String phone;
    private String address;
    private String city;
    private String state;
    private String zipCode;
    private Boolean isActive;
    private String credentialingStatus;
    private Double fraudRiskScore;
    private Boolean fraudFlagged;
    private Boolean eligibleForClaims;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Creates a DTO from a Provider entity.
     */
    public static ProviderResponseDTO fromEntity(Provider provider) {
        if (provider == null) return null;

        return ProviderResponseDTO.builder()
            .id(provider.getId())
            .npi(provider.getNpi())
            .name(provider.getName())
            .specialty(provider.getSpecialty())
            .taxId(provider.getTaxId())
            .inNetwork(provider.getInNetwork())
            .networkStatus(provider.getNetworkStatusDescription())
            .providerType(provider.getProviderType())
            .email(provider.getEmail())
            .phone(provider.getPhone())
            .address(provider.getAddress())
            .city(provider.getCity())
            .state(provider.getState())
            .zipCode(provider.getZipCode())
            .isActive(provider.getIsActive())
            .credentialingStatus(provider.getCredentialingStatus())
            .fraudRiskScore(provider.getFraudRiskScore())
            .fraudFlagged(provider.isFraudFlagged())
            .eligibleForClaims(provider.isEligibleForClaims())
            .createdAt(provider.getCreatedAt())
            .updatedAt(provider.getUpdatedAt())
            .build();
    }
}
