package com.healthcare.claims.api.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;

/**
 * DTO for patient creation and update requests.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientRequestDTO {

    @NotBlank(message = "Member ID is required")
    @Size(max = 20, message = "Member ID cannot exceed 20 characters")
    private String memberId;

    @NotBlank(message = "First name is required")
    @Size(max = 100, message = "First name cannot exceed 100 characters")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 100, message = "Last name cannot exceed 100 characters")
    private String lastName;

    @NotNull(message = "Date of birth is required")
    @Past(message = "Date of birth must be in the past")
    private LocalDate dateOfBirth;

    @NotBlank(message = "Policy number is required")
    @Size(max = 30, message = "Policy number cannot exceed 30 characters")
    private String policyNumber;

    @Size(max = 20, message = "Group number cannot exceed 20 characters")
    private String groupNumber;

    private LocalDate policyStartDate;

    private LocalDate policyEndDate;

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
}
