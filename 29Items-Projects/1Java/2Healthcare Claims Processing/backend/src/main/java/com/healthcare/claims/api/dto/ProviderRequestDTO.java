package com.healthcare.claims.api.dto;

import jakarta.validation.constraints.*;
import lombok.*;

/**
 * DTO for provider creation and update requests.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderRequestDTO {

    @NotBlank(message = "NPI is required")
    @Pattern(regexp = "^\\d{10}$", message = "NPI must be exactly 10 digits")
    private String npi;

    @NotBlank(message = "Provider name is required")
    @Size(max = 200, message = "Name cannot exceed 200 characters")
    private String name;

    @Size(max = 100, message = "Specialty cannot exceed 100 characters")
    private String specialty;

    @Size(max = 20, message = "Tax ID cannot exceed 20 characters")
    private String taxId;

    private Boolean inNetwork;

    @Size(max = 50, message = "Provider type cannot exceed 50 characters")
    private String providerType;

    @Email(message = "Invalid email format")
    @Size(max = 255, message = "Email cannot exceed 255 characters")
    private String email;

    @Size(max = 20, message = "Phone cannot exceed 20 characters")
    private String phone;

    @Size(max = 500, message = "Address cannot exceed 500 characters")
    private String address;

    @Size(max = 100, message = "City cannot exceed 100 characters")
    private String city;

    @Size(max = 50, message = "State cannot exceed 50 characters")
    private String state;

    @Size(max = 10, message = "Zip code cannot exceed 10 characters")
    private String zipCode;

    private Boolean isActive;

    @Size(max = 20, message = "Credentialing status cannot exceed 20 characters")
    private String credentialingStatus;
}
