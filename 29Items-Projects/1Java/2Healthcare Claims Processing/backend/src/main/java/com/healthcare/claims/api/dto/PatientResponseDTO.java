package com.healthcare.claims.api.dto;

import com.healthcare.claims.domain.model.Patient;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for patient responses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientResponseDTO {

    private UUID id;
    private String memberId;
    private String firstName;
    private String lastName;
    private String fullName;
    private LocalDate dateOfBirth;
    private Integer age;
    private String policyNumber;
    private String groupNumber;
    private LocalDate policyStartDate;
    private LocalDate policyEndDate;
    private Boolean policyActive;
    private String email;
    private String phone;
    private String address;
    private String city;
    private String state;
    private String zipCode;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Creates a DTO from a Patient entity.
     */
    public static PatientResponseDTO fromEntity(Patient patient) {
        if (patient == null) return null;

        return PatientResponseDTO.builder()
            .id(patient.getId())
            .memberId(patient.getMemberId())
            .firstName(patient.getFirstName())
            .lastName(patient.getLastName())
            .fullName(patient.getFullName())
            .dateOfBirth(patient.getDateOfBirth())
            .age(patient.getAge())
            .policyNumber(patient.getPolicyNumber())
            .groupNumber(patient.getGroupNumber())
            .policyStartDate(patient.getPolicyStartDate())
            .policyEndDate(patient.getPolicyEndDate())
            .policyActive(patient.isPolicyActive())
            .email(patient.getEmail())
            .phone(patient.getPhone())
            .address(patient.getAddress())
            .city(patient.getCity())
            .state(patient.getState())
            .zipCode(patient.getZipCode())
            .isActive(patient.getIsActive())
            .createdAt(patient.getCreatedAt())
            .updatedAt(patient.getUpdatedAt())
            .build();
    }
}
