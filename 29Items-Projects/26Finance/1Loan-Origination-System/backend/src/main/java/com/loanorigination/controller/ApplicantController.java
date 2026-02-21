package com.loanorigination.controller;

import com.loanorigination.model.Applicant;
import com.loanorigination.service.ApplicantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/applicants")
@RequiredArgsConstructor
@Slf4j
public class ApplicantController {

    private final ApplicantService applicantService;

    /**
     * Create a new applicant.
     *
     * POST /api/applicants
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('LOAN_OFFICER', 'ADMIN')")
    public ResponseEntity<Applicant> createApplicant(@Valid @RequestBody Applicant applicant) {
        log.info("POST /api/applicants");
        Applicant created = applicantService.createApplicant(applicant);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Retrieve a single applicant by ID.
     *
     * GET /api/applicants/{id}
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('LOAN_OFFICER', 'UNDERWRITER', 'ADMIN')")
    public ResponseEntity<Applicant> getApplicantById(@PathVariable Long id) {
        log.info("GET /api/applicants/{}", id);
        Applicant applicant = applicantService.getApplicantById(id);
        return ResponseEntity.ok(applicant);
    }

    /**
     * Retrieve all applicants.
     *
     * GET /api/applicants
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('LOAN_OFFICER', 'UNDERWRITER', 'ADMIN')")
    public ResponseEntity<List<Applicant>> getAllApplicants() {
        log.info("GET /api/applicants");
        List<Applicant> applicants = applicantService.getAllApplicants();
        return ResponseEntity.ok(applicants);
    }

    /**
     * Update an existing applicant's mutable fields.
     *
     * PUT /api/applicants/{id}
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('LOAN_OFFICER', 'ADMIN')")
    public ResponseEntity<Applicant> updateApplicant(
            @PathVariable Long id,
            @Valid @RequestBody Applicant applicant) {
        log.info("PUT /api/applicants/{}", id);
        Applicant updated = applicantService.updateApplicant(id, applicant);
        return ResponseEntity.ok(updated);
    }
}
