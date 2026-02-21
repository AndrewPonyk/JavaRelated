package com.loanorigination.service;

import java.math.BigDecimal;
import com.loanorigination.dto.CreditScoreRequest;
import com.loanorigination.dto.CreditScoreResponse;
import com.loanorigination.dto.UnderwritingResultDto;
import com.loanorigination.drools.UnderwritingRulesService;
import com.loanorigination.kafka.LoanEventProducer;
import com.loanorigination.ml.CreditScoringClient;
import com.loanorigination.model.Applicant;
import com.loanorigination.model.LoanApplication;
import com.loanorigination.model.UnderwritingDecision;
import com.loanorigination.repository.ApplicantRepository;
import com.loanorigination.repository.LoanApplicationRepository;
import com.loanorigination.repository.UnderwritingDecisionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;

@Service
@RequiredArgsConstructor
@Slf4j
public class UnderwritingService {

    private final LoanApplicationRepository applicationRepository;
    private final UnderwritingDecisionRepository decisionRepository;
    private final ApplicantRepository applicantRepository;
    private final CreditScoringClient creditScoringClient;
    private final UnderwritingRulesService rulesService;
    private final LoanEventProducer eventProducer;

    @Value("${application.features.ml-scoring-enabled:true}")
    private boolean mlScoringEnabled;

    @Value("${application.features.auto-underwriting-enabled:true}")
    private boolean autoUnderwritingEnabled;

    @Transactional
    public UnderwritingResultDto performUnderwriting(Long applicationId) {
        log.info("Starting underwriting for application: {}", applicationId);

        LoanApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + applicationId));

        Applicant applicant = applicantRepository.findById(application.getApplicantId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Applicant not found: " + application.getApplicantId()));

        CreditScoreResponse creditScore;
        if (mlScoringEnabled) {
            creditScore = getCreditScore(application, applicant);
        } else {
            log.info("ML scoring disabled — using rule-based fallback for application {}", applicationId);
            creditScore = getCreditScore(application, applicant);
        }

        application.setCreditScore(creditScore.getCreditScore());
        application.setDebtToIncomeRatio(calculateDTI(application, applicant));
        applicationRepository.save(application);

        if (!autoUnderwritingEnabled) {
            log.info("Auto-underwriting disabled — routing application {} to manual review", applicationId);
            UnderwritingDecision decision = buildManualReviewDecision(applicationId, creditScore);
            decision = decisionRepository.save(decision);
            application.setUnderwritingDecision(decision.getDecision().name());
            application.setDecisionDate(decision.getDecisionDate());
            application.setStatus(LoanApplication.ApplicationStatus.MANUAL_REVIEW_REQUIRED);
            applicationRepository.save(application);
            eventProducer.publishUnderwritingDecision(application, decision);
            return mapToDto(decision);
        }

        UnderwritingRulesService.UnderwritingDecision rulesDecision =
                rulesService.executeRules(application);

        UnderwritingDecision decision = new UnderwritingDecision();
        decision.setApplicationId(applicationId);
        decision.setDecision(mapDecision(rulesDecision.getDecision()));
        decision.setDecisionReason(rulesDecision.getReason());
        decision.setCreditScore(creditScore.getCreditScore());
        decision.setRiskScore(creditScore.getRiskScore() != null ? BigDecimal.valueOf(creditScore.getRiskScore()) : null);
        decision.setAutomated(true);

        if (rulesDecision.getConditions() != null && !rulesDecision.getConditions().isEmpty()) {
            decision.setConditions(String.join("; ", rulesDecision.getConditions()));
        }

        decision = decisionRepository.save(decision);

        application.setUnderwritingDecision(decision.getDecision().name());
        application.setDecisionDate(decision.getDecisionDate());
        application.setStatus(mapApplicationStatus(decision.getDecision()));
        applicationRepository.save(application);

        eventProducer.publishUnderwritingDecision(application, decision);

        return mapToDto(decision);
    }

    /**
     * Builds a credit score request using real applicant data.
     *
     * NOTE: existingDebt and loan history fields (numPreviousLoans, numDelinquencies) are
     * currently set to conservative defaults. In production these should be populated from
     * a credit bureau integration (e.g., Equifax, Experian, TransUnion).
     */
    private CreditScoreResponse getCreditScore(LoanApplication application, Applicant applicant) {
        CreditScoreRequest request = new CreditScoreRequest();

        double annualIncome = applicant.getAnnualIncome() != null
                ? applicant.getAnnualIncome().doubleValue()
                : 0.0;
        request.setAnnualIncome(annualIncome);

        // TODO: Replace with credit bureau data when available
        request.setExistingDebt(0.0);

        request.setLoanAmount(application.getLoanAmount().doubleValue());

        double employmentYears = applicant.getYearsEmployed() != null
                ? applicant.getYearsEmployed().doubleValue()
                : 0.0;
        request.setEmploymentYears(employmentYears);

        int age = applicant.getDateOfBirth() != null
                ? Period.between(applicant.getDateOfBirth(), LocalDate.now()).getYears()
                : 35; // conservative default when DOB is missing
        request.setAge(age);

        // TODO: Replace with credit bureau data when available
        request.setNumPreviousLoans(0);
        request.setNumDelinquencies(0);

        return creditScoringClient.getCreditScore(request);
    }

    /**
     * Calculates the debt-to-income ratio from real applicant and loan data.
     *
     * Monthly income = annualIncome / 12
     * Monthly payment = standard amortisation formula using a 6% default rate assumption.
     * existingDebtMonthlyPayment defaults to 0 until credit bureau integration provides real data.
     *
     * TODO: Add existingDebtMonthlyPayment from credit bureau when available.
     */
    private BigDecimal calculateDTI(LoanApplication application, Applicant applicant) {
        if (applicant.getAnnualIncome() == null
                || applicant.getAnnualIncome().compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Applicant {} has no annual income — DTI defaulting to 1.0", applicant.getId());
            return BigDecimal.ONE;
        }

        BigDecimal monthlyIncome = applicant.getAnnualIncome()
                .divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP);

        // Monthly payment via annuity formula:  P * r / (1 - (1+r)^-n)
        // Using a conservative default rate of 6% APR until the rate is finalised.
        double defaultAnnualRate = 0.06;
        double monthlyRate = defaultAnnualRate / 12.0;
        int termMonths = application.getLoanTermMonths() != null ? application.getLoanTermMonths() : 360;
        double loanAmount = application.getLoanAmount().doubleValue();

        double monthlyPayment;
        if (monthlyRate == 0) {
            monthlyPayment = loanAmount / termMonths;
        } else {
            monthlyPayment = loanAmount * monthlyRate
                    / (1.0 - Math.pow(1.0 + monthlyRate, -termMonths));
        }

        // TODO: add existing monthly debt payments from credit bureau data
        double totalMonthlyDebt = monthlyPayment; // + existingDebtMonthlyPayments

        BigDecimal dti = BigDecimal.valueOf(totalMonthlyDebt)
                .divide(monthlyIncome, 4, RoundingMode.HALF_UP);

        // Clamp at 1.0 — a DTI above 100% is still effectively a reject signal
        return dti.compareTo(BigDecimal.ONE) > 0 ? BigDecimal.ONE : dti;
    }

    private UnderwritingDecision buildManualReviewDecision(Long applicationId,
                                                            CreditScoreResponse creditScore) {
        UnderwritingDecision decision = new UnderwritingDecision();
        decision.setApplicationId(applicationId);
        decision.setDecision(UnderwritingDecision.Decision.MANUAL_REVIEW);
        decision.setDecisionReason("Auto-underwriting is disabled — routed to manual review");
        decision.setCreditScore(creditScore.getCreditScore());
        decision.setRiskScore(creditScore.getRiskScore() != null ? BigDecimal.valueOf(creditScore.getRiskScore()) : null);
        decision.setAutomated(false);
        return decision;
    }

    private UnderwritingDecision.Decision mapDecision(String decision) {
        if (decision == null) return UnderwritingDecision.Decision.MANUAL_REVIEW;

        return switch (decision) {
            case "APPROVED" -> UnderwritingDecision.Decision.APPROVED;
            case "REJECTED" -> UnderwritingDecision.Decision.REJECTED;
            default -> UnderwritingDecision.Decision.MANUAL_REVIEW;
        };
    }

    private LoanApplication.ApplicationStatus mapApplicationStatus(UnderwritingDecision.Decision decision) {
        return switch (decision) {
            case APPROVED -> LoanApplication.ApplicationStatus.APPROVED;
            case REJECTED -> LoanApplication.ApplicationStatus.REJECTED;
            case MANUAL_REVIEW -> LoanApplication.ApplicationStatus.MANUAL_REVIEW_REQUIRED;
        };
    }

    private UnderwritingResultDto mapToDto(UnderwritingDecision decision) {
        UnderwritingResultDto dto = new UnderwritingResultDto();
        dto.setApplicationId(decision.getApplicationId());
        dto.setDecision(decision.getDecision().name());
        dto.setReason(decision.getDecisionReason());
        dto.setCreditScore(decision.getCreditScore());
        dto.setRiskScore(decision.getRiskScore() != null ? decision.getRiskScore().doubleValue() : null);
        dto.setAutomated(decision.isAutomated());
        dto.setDecisionDate(decision.getDecisionDate());

        if (decision.getConditions() != null) {
            dto.setConditions(decision.getConditions().split(";\\s*"));
        }

        return dto;
    }

    public static class ResourceNotFoundException extends RuntimeException {
        public ResourceNotFoundException(String message) {
            super(message);
        }
    }
}
