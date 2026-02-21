package com.loanorigination.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "applicant")
@Data
public class Applicant {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "applicant_seq")
    @SequenceGenerator(name = "applicant_seq", sequenceName = "applicant_seq", allocationSize = 1)
    private Long id;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "ssn", length = 11)
    private String ssn;

    @Column(name = "annual_income", precision = 15, scale = 2)
    private BigDecimal annualIncome;

    @Column(name = "employment_status", length = 50)
    private String employmentStatus;

    @Column(name = "employer_name")
    private String employerName;

    @Column(name = "years_employed", precision = 3, scale = 1)
    private BigDecimal yearsEmployed;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }
}
