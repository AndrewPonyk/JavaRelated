package com.loanorigination.service;

import com.loanorigination.dto.LoanApplicationDto;
import com.loanorigination.kafka.LoanEventProducer;
import com.loanorigination.model.LoanApplication;
import com.loanorigination.model.LoanApplication.ApplicationStatus;
import com.loanorigination.repository.ApplicantRepository;
import com.loanorigination.repository.LoanApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoanApplicationService {

    private final LoanApplicationRepository loanApplicationRepository;
    private final ApplicantRepository applicantRepository;
    private final LoanEventProducer loanEventProducer;

    /**
     * Defines the valid status transitions for a loan application.
     * Any transition not present in this map is illegal and will throw an exception.
     */
    private static final Map<ApplicationStatus, Set<ApplicationStatus>> ALLOWED_TRANSITIONS =
            new HashMap<>();

    static {
        ALLOWED_TRANSITIONS.put(ApplicationStatus.SUBMITTED,
                EnumSet.of(ApplicationStatus.UNDER_REVIEW));

        ALLOWED_TRANSITIONS.put(ApplicationStatus.UNDER_REVIEW,
                EnumSet.of(ApplicationStatus.APPROVED,
                        ApplicationStatus.REJECTED,
                        ApplicationStatus.MANUAL_REVIEW_REQUIRED));

        ALLOWED_TRANSITIONS.put(ApplicationStatus.MANUAL_REVIEW_REQUIRED,
                EnumSet.of(ApplicationStatus.APPROVED, ApplicationStatus.REJECTED));
    }

    /** Statuses that indicate an application is still active (blocks duplicate submission). */
    private static final Set<ApplicationStatus> ACTIVE_STATUSES = EnumSet.of(
            ApplicationStatus.SUBMITTED,
            ApplicationStatus.UNDER_REVIEW,
            ApplicationStatus.MANUAL_REVIEW_REQUIRED
    );

    @Transactional
    public LoanApplication submitApplication(LoanApplicationDto dto) {
        log.info("Submitting loan application for amount: {}", dto.getLoanAmount());

        validateApplication(dto);

        // Verify applicant exists before creating the application
        if (!applicantRepository.existsById(dto.getApplicantId())) {
            throw new ResourceNotFoundException(
                    "Applicant not found with id: " + dto.getApplicantId());
        }

        // Prevent duplicate active applications for the same applicant
        List<LoanApplication> existing =
                loanApplicationRepository.findByApplicantId(dto.getApplicantId());
        boolean hasActiveApplication = existing.stream()
                .anyMatch(app -> ACTIVE_STATUSES.contains(app.getStatus()));
        if (hasActiveApplication) {
            throw new BusinessRuleException(
                    "Applicant " + dto.getApplicantId()
                    + " already has an active loan application. "
                    + "A new application can only be submitted after the current one is resolved.");
        }

        LoanApplication application = new LoanApplication();
        application.setApplicationId("LA-" + UUID.randomUUID().toString().substring(0, 8));
        application.setLoanAmount(dto.getLoanAmount());
        application.setLoanPurpose(dto.getLoanPurpose());
        application.setLoanTermMonths(dto.getLoanTermMonths());
        application.setApplicantId(dto.getApplicantId());
        application.setStatus(ApplicationStatus.SUBMITTED);

        LoanApplication savedApplication = loanApplicationRepository.save(application);

        loanEventProducer.publishApplicationSubmitted(savedApplication);

        log.info("Loan application submitted successfully: {}", savedApplication.getApplicationId());
        return savedApplication;
    }

    public LoanApplication getApplicationById(Long id) {
        return loanApplicationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + id));
    }

    public List<LoanApplication> getAllApplications(String status) {
        if (status != null) {
            ApplicationStatus applicationStatus = ApplicationStatus.valueOf(status.toUpperCase());
            return loanApplicationRepository.findByStatus(applicationStatus);
        }
        return loanApplicationRepository.findAll();
    }

    @Transactional
    public LoanApplication updateStatus(Long id, String status) {
        LoanApplication application = getApplicationById(id);
        ApplicationStatus newStatus = ApplicationStatus.valueOf(status.toUpperCase());

        validateStatusTransition(application.getStatus(), newStatus);

        application.setStatus(newStatus);
        LoanApplication updated = loanApplicationRepository.save(application);

        loanEventProducer.publishApplicationStatusChanged(updated);

        return updated;
    }

    /**
     * Validates that a status transition is permitted.
     * Terminal statuses (APPROVED, REJECTED, FUNDED) cannot be transitioned out of.
     */
    private void validateStatusTransition(ApplicationStatus current, ApplicationStatus target) {
        Set<ApplicationStatus> allowed = ALLOWED_TRANSITIONS.get(current);
        if (allowed == null || !allowed.contains(target)) {
            throw new IllegalStateException(
                    String.format("Invalid status transition: %s -> %s. "
                            + "Allowed transitions from %s: %s",
                            current, target, current,
                            allowed != null ? allowed : "none (terminal status)"));
        }
    }

    private void validateApplication(LoanApplicationDto dto) {
        if (dto.getLoanAmount() == null || dto.getLoanAmount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("Loan amount must be positive");
        }

        if (dto.getLoanAmount().compareTo(new java.math.BigDecimal("1000000")) > 0) {
            throw new BusinessRuleException("Loan amount cannot exceed $1,000,000");
        }

        if (dto.getApplicantId() == null) {
            throw new BusinessRuleException("Applicant ID is required");
        }

        if (dto.getLoanPurpose() == null || dto.getLoanPurpose().trim().isEmpty()) {
            throw new BusinessRuleException("Loan purpose is required");
        }

        if (dto.getLoanTermMonths() == null || dto.getLoanTermMonths() < 12 || dto.getLoanTermMonths() > 360) {
            throw new BusinessRuleException("Loan term must be between 12 and 360 months");
        }
    }

    public static class ResourceNotFoundException extends RuntimeException {
        public ResourceNotFoundException(String message) {
            super(message);
        }
    }

    public static class BusinessRuleException extends RuntimeException {
        public BusinessRuleException(String message) {
            super(message);
        }
    }
}
