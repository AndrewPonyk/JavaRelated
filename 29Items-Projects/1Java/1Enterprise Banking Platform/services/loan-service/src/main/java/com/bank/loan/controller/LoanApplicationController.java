package com.bank.loan.controller;

import com.bank.loan.dto.LoanApplicationRequest;
import com.bank.loan.dto.LoanApplicationResponse;
import com.bank.loan.service.LoanApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * REST controller for loan applications.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/loan-applications")
@RequiredArgsConstructor
@Tag(name = "Loan Applications", description = "Loan application management APIs")
public class LoanApplicationController {

    private final LoanApplicationService applicationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create loan application", description = "Submit a new loan application")
    public Mono<LoanApplicationResponse> createApplication(
            @Valid @RequestBody LoanApplicationRequest request) {
        log.info("Received loan application for: {}", request.getApplicantEmail());
        return applicationService.createApplication(request);
    }

    @GetMapping("/{applicationId}")
    @Operation(summary = "Get application by ID", description = "Retrieve loan application details")
    public Mono<LoanApplicationResponse> getApplication(@PathVariable UUID applicationId) {
        log.debug("Fetching application: {}", applicationId);
        return applicationService.getApplication(applicationId);
    }

    @GetMapping("/number/{applicationNumber}")
    @Operation(summary = "Get application by number", description = "Retrieve loan application by application number")
    public Mono<LoanApplicationResponse> getApplicationByNumber(@PathVariable String applicationNumber) {
        log.debug("Fetching application by number: {}", applicationNumber);
        return applicationService.getApplicationByNumber(applicationNumber);
    }

    @GetMapping(value = "/applicant/{applicantId}", produces = MediaType.APPLICATION_NDJSON_VALUE)
    @Operation(summary = "Get applicant's applications", description = "Retrieve all applications for an applicant")
    public Flux<LoanApplicationResponse> getApplicationsByApplicant(@PathVariable UUID applicantId) {
        log.debug("Fetching applications for applicant: {}", applicantId);
        return applicationService.getApplicationsByApplicant(applicantId);
    }

    @GetMapping(value = "/pending-review", produces = MediaType.APPLICATION_NDJSON_VALUE)
    @Operation(summary = "Get pending review applications", description = "Retrieve applications awaiting review")
    public Flux<LoanApplicationResponse> getPendingReviewApplications() {
        log.debug("Fetching pending review applications");
        return applicationService.getPendingReviewApplications();
    }

    @PostMapping("/{applicationId}/approve")
    @Operation(summary = "Approve application", description = "Approve a loan application")
    public Mono<LoanApplicationResponse> approveApplication(
            @PathVariable UUID applicationId,
            @RequestParam @NotBlank @Size(max = 100) String reviewedBy,
            @RequestParam @Positive BigDecimal approvedAmount,
            @RequestParam @Positive BigDecimal interestRate) {
        log.info("Approving application: {}", applicationId);
        return applicationService.approveApplication(applicationId, reviewedBy, approvedAmount, interestRate);
    }

    @PostMapping("/{applicationId}/reject")
    @Operation(summary = "Reject application", description = "Reject a loan application")
    public Mono<LoanApplicationResponse> rejectApplication(
            @PathVariable UUID applicationId,
            @RequestParam @NotBlank @Size(max = 100) String reviewedBy,
            @RequestParam @NotBlank @Size(max = 500) String reason) {
        log.info("Rejecting application: {}", applicationId);
        return applicationService.rejectApplication(applicationId, reviewedBy, reason);
    }

    @PostMapping("/{applicationId}/request-documents")
    @Operation(summary = "Request documents", description = "Request additional documents for an application")
    public Mono<LoanApplicationResponse> requestDocuments(
            @PathVariable UUID applicationId,
            @RequestParam @NotBlank @Size(max = 100) String reviewedBy) {
        log.info("Requesting documents for application: {}", applicationId);
        return applicationService.requestDocuments(applicationId, reviewedBy);
    }

    @PostMapping("/{applicationId}/cancel")
    @Operation(summary = "Cancel application", description = "Cancel a loan application")
    public Mono<LoanApplicationResponse> cancelApplication(@PathVariable UUID applicationId) {
        log.info("Cancelling application: {}", applicationId);
        return applicationService.cancelApplication(applicationId);
    }

    @GetMapping("/stats")
    @Operation(summary = "Get application statistics", description = "Retrieve application statistics")
    public Mono<LoanApplicationService.ApplicationStatistics> getStatistics() {
        log.debug("Fetching application statistics");
        return applicationService.getStatistics();
    }
}
