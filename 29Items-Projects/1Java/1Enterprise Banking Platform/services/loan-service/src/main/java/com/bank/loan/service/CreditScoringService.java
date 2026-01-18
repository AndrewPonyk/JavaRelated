package com.bank.loan.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Random;
import java.util.UUID;

/**
 * Service for credit scoring.
 * In production, this would integrate with credit bureaus (Equifax, Experian, TransUnion).
 */
@Slf4j
@Service
public class CreditScoringService {

    private final Random random = new Random();

    /**
     * Get credit score for an applicant.
     * In production, this would call external credit bureau APIs.
     *
     * @param applicantId the applicant's ID
     * @return the credit score (300-850)
     */
    public Mono<Integer> getCreditScore(UUID applicantId) {
        log.info("Fetching credit score for applicant: {}", applicantId);

        return Mono.fromSupplier(() -> {
            // Simulated credit score generation
            // In production, integrate with credit bureaus
            int baseScore = 650;
            int variation = random.nextInt(200) - 100; // -100 to +100
            int score = Math.max(300, Math.min(850, baseScore + variation));

            log.debug("Credit score for {}: {}", applicantId, score);
            return score;
        });
    }

    /**
     * Get detailed credit report.
     * In production, this would return full credit history.
     */
    public Mono<CreditReport> getCreditReport(UUID applicantId) {
        log.info("Fetching credit report for applicant: {}", applicantId);

        return getCreditScore(applicantId)
            .map(score -> CreditReport.builder()
                .applicantId(applicantId)
                .creditScore(score)
                .paymentHistory(calculatePaymentHistory(score))
                .creditUtilization(calculateCreditUtilization(score))
                .creditHistoryLength(random.nextInt(20) + 1)
                .recentInquiries(random.nextInt(5))
                .derogatoriesCount(score < 600 ? random.nextInt(3) : 0)
                .build());
    }

    private String calculatePaymentHistory(int score) {
        if (score >= 750) return "EXCELLENT";
        if (score >= 700) return "GOOD";
        if (score >= 650) return "FAIR";
        return "POOR";
    }

    private double calculateCreditUtilization(int score) {
        // Lower utilization for higher scores
        return Math.max(0.1, (850.0 - score) / 850.0);
    }

    @lombok.Data
    @lombok.Builder
    public static class CreditReport {
        private UUID applicantId;
        private int creditScore;
        private String paymentHistory;
        private double creditUtilization;
        private int creditHistoryLength;
        private int recentInquiries;
        private int derogatoriesCount;
    }
}
