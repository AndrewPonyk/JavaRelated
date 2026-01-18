package com.bank.loan.service;

import com.bank.loan.dto.LoanApplicationRequest;
import com.bank.loan.dto.LoanApplicationResponse;
import com.bank.loan.model.LoanApplication;
import com.bank.loan.model.LoanApplication.ApplicationStatus;
import com.bank.loan.repository.LoanApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/**
 * Service for loan application processing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoanApplicationService {

    private final LoanApplicationRepository applicationRepository;
    private final CreditScoringService creditScoringService;
    private final ApplicationNumberGenerator applicationNumberGenerator;

    // Interest rate ranges by credit score
    private static final BigDecimal BASE_RATE = new BigDecimal("0.05"); // 5%
    private static final int MIN_CREDIT_SCORE = 300;
    private static final int EXCELLENT_CREDIT_SCORE = 750;
    private static final BigDecimal DEBT_TO_INCOME_MAX = new BigDecimal("0.43"); // 43%

    /**
     * Create a new loan application.
     */
    @Transactional
    public Mono<LoanApplicationResponse> createApplication(LoanApplicationRequest request) {
        log.info("Creating loan application for applicant: {}", request.getApplicantEmail());

        return applicationNumberGenerator.generate()
            .flatMap(appNumber -> {
                LoanApplication application = LoanApplication.builder()
                    .id(UUID.randomUUID())
                    .applicationNumber(appNumber)
                    .applicantId(request.getApplicantId())
                    .applicantName(request.getApplicantName())
                    .applicantEmail(request.getApplicantEmail())
                    .loanType(request.getLoanType())
                    .requestedAmount(request.getRequestedAmount())
                    .currency(request.getCurrency())
                    .termMonths(request.getTermMonths())
                    .annualIncome(request.getAnnualIncome())
                    .employmentStatus(request.getEmploymentStatus())
                    .purpose(request.getPurpose())
                    .collateralDescription(request.getCollateralDescription())
                    .collateralValue(request.getCollateralValue())
                    .status(ApplicationStatus.SUBMITTED)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

                return applicationRepository.save(application);
            })
            .flatMap(this::performInitialUnderwriting)
            .map(this::mapToResponse)
            .doOnSuccess(resp -> log.info("Application created: {}", resp.getApplicationNumber()));
    }

    /**
     * Perform initial automated underwriting.
     */
    private Mono<LoanApplication> performInitialUnderwriting(LoanApplication application) {
        log.info("Performing initial underwriting for application: {}", application.getApplicationNumber());

        return creditScoringService.getCreditScore(application.getApplicantId())
            .flatMap(creditScore -> {
                application.setCreditScore(creditScore);

                // Check debt-to-income ratio
                BigDecimal monthlyIncome = application.getAnnualIncome().divide(
                    BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
                BigDecimal estimatedPayment = calculateMonthlyPayment(
                    application.getRequestedAmount(),
                    getInterestRate(creditScore),
                    application.getTermMonths()
                );
                BigDecimal dti = estimatedPayment.divide(monthlyIncome, 4, RoundingMode.HALF_UP);

                // Auto-reject based on credit score
                if (creditScore < MIN_CREDIT_SCORE) {
                    application.setStatus(ApplicationStatus.REJECTED);
                    application.setRejectionReason("Credit score below minimum threshold");
                    log.info("Application {} auto-rejected: low credit score", application.getApplicationNumber());
                    return applicationRepository.save(application);
                }

                // Auto-reject based on DTI
                if (dti.compareTo(DEBT_TO_INCOME_MAX) > 0) {
                    application.setStatus(ApplicationStatus.REJECTED);
                    application.setRejectionReason("Debt-to-income ratio exceeds maximum allowed");
                    log.info("Application {} auto-rejected: high DTI", application.getApplicationNumber());
                    return applicationRepository.save(application);
                }

                // Set preliminary terms
                BigDecimal interestRate = getInterestRate(creditScore);
                application.setInterestRate(interestRate);
                application.setMonthlyPayment(calculateMonthlyPayment(
                    application.getRequestedAmount(), interestRate, application.getTermMonths()));

                // Auto-approve for excellent credit scores
                if (creditScore >= EXCELLENT_CREDIT_SCORE &&
                    application.getRequestedAmount().compareTo(new BigDecimal("50000")) <= 0) {
                    application.setStatus(ApplicationStatus.APPROVED);
                    application.setApprovedAmount(application.getRequestedAmount());
                    application.setApprovedAt(Instant.now());
                    application.setReviewedBy("AUTOMATED_UNDERWRITING");
                    log.info("Application {} auto-approved", application.getApplicationNumber());
                } else {
                    application.setStatus(ApplicationStatus.UNDER_REVIEW);
                    log.info("Application {} sent for manual review", application.getApplicationNumber());
                }

                return applicationRepository.save(application);
            });
    }

    /**
     * Get application by ID.
     */
    public Mono<LoanApplicationResponse> getApplication(UUID applicationId) {
        return applicationRepository.findById(applicationId)
            .switchIfEmpty(Mono.error(new ApplicationNotFoundException(applicationId)))
            .map(this::mapToResponse);
    }

    /**
     * Get application by number.
     */
    public Mono<LoanApplicationResponse> getApplicationByNumber(String applicationNumber) {
        return applicationRepository.findByApplicationNumber(applicationNumber)
            .switchIfEmpty(Mono.error(new ApplicationNotFoundException(applicationNumber)))
            .map(this::mapToResponse);
    }

    /**
     * Get all applications for an applicant.
     */
    public Flux<LoanApplicationResponse> getApplicationsByApplicant(UUID applicantId) {
        return applicationRepository.findByApplicantId(applicantId)
            .map(this::mapToResponse);
    }

    /**
     * Get applications pending review.
     */
    public Flux<LoanApplicationResponse> getPendingReviewApplications() {
        return applicationRepository.findPendingReview()
            .map(this::mapToResponse);
    }

    /**
     * Manually approve an application.
     */
    @Transactional
    public Mono<LoanApplicationResponse> approveApplication(UUID applicationId, String reviewedBy,
                                                            BigDecimal approvedAmount,
                                                            BigDecimal interestRate) {
        log.info("Approving application: {}", applicationId);

        return applicationRepository.findById(applicationId)
            .switchIfEmpty(Mono.error(new ApplicationNotFoundException(applicationId)))
            .flatMap(application -> {
                if (application.getStatus() != ApplicationStatus.UNDER_REVIEW) {
                    return Mono.error(new IllegalStateException(
                        "Can only approve applications under review. Current status: " +
                            application.getStatus()));
                }

                application.setStatus(ApplicationStatus.APPROVED);
                application.setApprovedAmount(approvedAmount);
                application.setInterestRate(interestRate);
                application.setMonthlyPayment(calculateMonthlyPayment(
                    approvedAmount, interestRate, application.getTermMonths()));
                application.setReviewedBy(reviewedBy);
                application.setApprovedAt(Instant.now());
                application.setUpdatedAt(Instant.now());

                return applicationRepository.save(application);
            })
            .map(this::mapToResponse);
    }

    /**
     * Reject an application.
     */
    @Transactional
    public Mono<LoanApplicationResponse> rejectApplication(UUID applicationId, String reviewedBy,
                                                           String reason) {
        log.info("Rejecting application: {}", applicationId);

        return applicationRepository.findById(applicationId)
            .switchIfEmpty(Mono.error(new ApplicationNotFoundException(applicationId)))
            .flatMap(application -> {
                if (application.getStatus() != ApplicationStatus.UNDER_REVIEW &&
                    application.getStatus() != ApplicationStatus.DOCUMENTS_REQUIRED) {
                    return Mono.error(new IllegalStateException(
                        "Cannot reject application in status: " + application.getStatus()));
                }

                application.setStatus(ApplicationStatus.REJECTED);
                application.setRejectionReason(reason);
                application.setReviewedBy(reviewedBy);
                application.setUpdatedAt(Instant.now());

                return applicationRepository.save(application);
            })
            .map(this::mapToResponse);
    }

    /**
     * Request additional documents.
     */
    @Transactional
    public Mono<LoanApplicationResponse> requestDocuments(UUID applicationId, String reviewedBy) {
        log.info("Requesting documents for application: {}", applicationId);

        return applicationRepository.findById(applicationId)
            .switchIfEmpty(Mono.error(new ApplicationNotFoundException(applicationId)))
            .flatMap(application -> {
                if (application.getStatus() != ApplicationStatus.UNDER_REVIEW) {
                    return Mono.error(new IllegalStateException(
                        "Can only request documents for applications under review"));
                }

                application.setStatus(ApplicationStatus.DOCUMENTS_REQUIRED);
                application.setReviewedBy(reviewedBy);
                application.setUpdatedAt(Instant.now());

                return applicationRepository.save(application);
            })
            .map(this::mapToResponse);
    }

    /**
     * Cancel an application.
     */
    @Transactional
    public Mono<LoanApplicationResponse> cancelApplication(UUID applicationId) {
        log.info("Cancelling application: {}", applicationId);

        return applicationRepository.findById(applicationId)
            .switchIfEmpty(Mono.error(new ApplicationNotFoundException(applicationId)))
            .flatMap(application -> {
                if (application.getStatus() == ApplicationStatus.DISBURSED ||
                    application.getStatus() == ApplicationStatus.CLOSED) {
                    return Mono.error(new IllegalStateException(
                        "Cannot cancel application in status: " + application.getStatus()));
                }

                application.setStatus(ApplicationStatus.CANCELLED);
                application.setUpdatedAt(Instant.now());

                return applicationRepository.save(application);
            })
            .map(this::mapToResponse);
    }

    /**
     * Get application statistics.
     */
    public Mono<ApplicationStatistics> getStatistics() {
        return Mono.zip(
            applicationRepository.countByStatus(ApplicationStatus.SUBMITTED),
            applicationRepository.countByStatus(ApplicationStatus.UNDER_REVIEW),
            applicationRepository.countByStatus(ApplicationStatus.APPROVED),
            applicationRepository.countByStatus(ApplicationStatus.REJECTED),
            applicationRepository.countByStatus(ApplicationStatus.DISBURSED)
        ).map(tuple -> ApplicationStatistics.builder()
            .submitted(tuple.getT1())
            .underReview(tuple.getT2())
            .approved(tuple.getT3())
            .rejected(tuple.getT4())
            .disbursed(tuple.getT5())
            .build());
    }

    private BigDecimal getInterestRate(int creditScore) {
        // Higher credit score = lower interest rate
        double adjustment = Math.max(0, (850 - creditScore) / 850.0) * 0.10; // Up to 10% adjustment
        return BASE_RATE.add(BigDecimal.valueOf(adjustment)).setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateMonthlyPayment(BigDecimal principal, BigDecimal annualRate, int months) {
        // Monthly payment = P * [r(1+r)^n] / [(1+r)^n - 1]
        BigDecimal monthlyRate = annualRate.divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP);
        double r = monthlyRate.doubleValue();
        double p = principal.doubleValue();
        int n = months;

        if (r == 0) {
            return principal.divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
        }

        double payment = p * (r * Math.pow(1 + r, n)) / (Math.pow(1 + r, n) - 1);
        return BigDecimal.valueOf(payment).setScale(2, RoundingMode.HALF_UP);
    }

    private LoanApplicationResponse mapToResponse(LoanApplication application) {
        return LoanApplicationResponse.builder()
            .id(application.getId())
            .applicationNumber(application.getApplicationNumber())
            .applicantId(application.getApplicantId())
            .applicantName(application.getApplicantName())
            .applicantEmail(application.getApplicantEmail())
            .loanType(application.getLoanType())
            .requestedAmount(application.getRequestedAmount())
            .approvedAmount(application.getApprovedAmount())
            .currency(application.getCurrency())
            .termMonths(application.getTermMonths())
            .interestRate(application.getInterestRate())
            .monthlyPayment(application.getMonthlyPayment())
            .status(application.getStatus())
            .creditScore(application.getCreditScore())
            .annualIncome(application.getAnnualIncome())
            .employmentStatus(application.getEmploymentStatus())
            .purpose(application.getPurpose())
            .rejectionReason(application.getRejectionReason())
            .reviewedBy(application.getReviewedBy())
            .createdAt(application.getCreatedAt())
            .approvedAt(application.getApprovedAt())
            .build();
    }

    @lombok.Data
    @lombok.Builder
    public static class ApplicationStatistics {
        private long submitted;
        private long underReview;
        private long approved;
        private long rejected;
        private long disbursed;
    }

    public static class ApplicationNotFoundException extends RuntimeException {
        public ApplicationNotFoundException(UUID applicationId) {
            super("Application not found: " + applicationId);
        }
        public ApplicationNotFoundException(String applicationNumber) {
            super("Application not found with number: " + applicationNumber);
        }
    }
}
