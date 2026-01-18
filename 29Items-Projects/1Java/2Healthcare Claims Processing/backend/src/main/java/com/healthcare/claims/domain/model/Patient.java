package com.healthcare.claims.domain.model;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a patient/member in the healthcare system.
 */
@Entity
@Table(name = "patients", indexes = {
    @Index(name = "idx_patients_member_id", columnList = "member_id"),
    @Index(name = "idx_patients_policy_number", columnList = "policy_number")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Patient extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotBlank
    @Column(name = "member_id", unique = true, nullable = false, length = 20)
    private String memberId;

    @NotBlank
    @Size(max = 100)
    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @NotBlank
    @Size(max = 100)
    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @NotNull
    @Past(message = "Date of birth must be in the past")
    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @NotBlank
    @Column(name = "policy_number", nullable = false, length = 30)
    private String policyNumber;

    @Column(name = "group_number", length = 20)
    private String groupNumber;

    @Column(name = "policy_start_date")
    private LocalDate policyStartDate;

    @Column(name = "policy_end_date")
    private LocalDate policyEndDate;

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

    @Version
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Returns the patient's full name.
     */
    public String getFullName() {
        return firstName + " " + lastName;
    }

    /**
     * Checks if the patient's policy is currently active.
     */
    public boolean isPolicyActive() {
        if (!isActive) return false;
        LocalDate today = LocalDate.now();
        boolean afterStart = policyStartDate == null || !today.isBefore(policyStartDate);
        boolean beforeEnd = policyEndDate == null || !today.isAfter(policyEndDate);
        return afterStart && beforeEnd;
    }

    /**
     * Calculates the patient's age.
     */
    public int getAge() {
        return java.time.Period.between(dateOfBirth, LocalDate.now()).getYears();
    }
}
