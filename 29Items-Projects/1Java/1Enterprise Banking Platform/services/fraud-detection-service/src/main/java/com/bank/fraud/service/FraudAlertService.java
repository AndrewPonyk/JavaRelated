package com.bank.fraud.service;

import com.bank.fraud.dto.FraudCheckRequest;
import com.bank.fraud.dto.FraudCheckResponse;
import com.bank.fraud.model.FraudAlert;
import com.bank.fraud.model.FraudAlert.AlertStatus;
import com.bank.fraud.model.FraudAlert.RiskLevel;
import com.bank.fraud.repository.FraudAlertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Service for managing fraud alerts.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FraudAlertService {

    private final FraudAlertRepository alertRepository;

    /**
     * Create a fraud alert from a fraud check result.
     */
    @Transactional
    public Mono<FraudAlert> createAlert(FraudCheckRequest request, FraudCheckResponse response) {
        if (response.getRiskLevel() == FraudCheckResponse.RiskLevel.LOW) {
            // Don't create alerts for low risk transactions
            return Mono.empty();
        }

        log.info("Creating fraud alert for transaction: {}, risk: {}",
            request.getTransactionId(), response.getRiskScore());

        FraudAlert alert = FraudAlert.builder()
            .id(UUID.randomUUID())
            .transactionId(request.getTransactionId())
            .accountId(request.getSourceAccountId())
            .amount(request.getAmount())
            .currency(request.getCurrency())
            .riskScore(response.getRiskScore())
            .riskLevel(convertRiskLevel(response.getRiskLevel()))
            .riskFactors(String.join(",", response.getRiskFactors()))
            .status(AlertStatus.PENDING)
            .createdAt(Instant.now())
            .build();

        return alertRepository.save(alert);
    }

    /**
     * Get alert by ID.
     */
    public Mono<FraudAlert> getAlert(UUID alertId) {
        return alertRepository.findById(alertId)
            .switchIfEmpty(Mono.error(new AlertNotFoundException(alertId)));
    }

    /**
     * Get alerts by account.
     */
    public Flux<FraudAlert> getAlertsByAccount(UUID accountId) {
        return alertRepository.findByAccountId(accountId);
    }

    /**
     * Get pending alerts ordered by risk.
     */
    public Flux<FraudAlert> getPendingAlerts() {
        return alertRepository.findPendingAlertsByRiskScore();
    }

    /**
     * Get critical pending alerts.
     */
    public Flux<FraudAlert> getCriticalAlerts() {
        return alertRepository.findCriticalPendingAlerts();
    }

    /**
     * Mark alert as confirmed fraud.
     */
    @Transactional
    public Mono<FraudAlert> confirmFraud(UUID alertId, String reviewedBy, String notes) {
        return alertRepository.findById(alertId)
            .switchIfEmpty(Mono.error(new AlertNotFoundException(alertId)))
            .flatMap(alert -> {
                alert.setStatus(AlertStatus.CONFIRMED_FRAUD);
                alert.setReviewedBy(reviewedBy);
                alert.setReviewNotes(notes);
                alert.setReviewedAt(Instant.now());
                return alertRepository.save(alert);
            });
    }

    /**
     * Mark alert as false positive.
     */
    @Transactional
    public Mono<FraudAlert> markFalsePositive(UUID alertId, String reviewedBy, String notes) {
        return alertRepository.findById(alertId)
            .switchIfEmpty(Mono.error(new AlertNotFoundException(alertId)))
            .flatMap(alert -> {
                alert.setStatus(AlertStatus.FALSE_POSITIVE);
                alert.setReviewedBy(reviewedBy);
                alert.setReviewNotes(notes);
                alert.setReviewedAt(Instant.now());
                return alertRepository.save(alert);
            });
    }

    /**
     * Escalate alert.
     */
    @Transactional
    public Mono<FraudAlert> escalateAlert(UUID alertId, String reviewedBy, String notes) {
        return alertRepository.findById(alertId)
            .switchIfEmpty(Mono.error(new AlertNotFoundException(alertId)))
            .flatMap(alert -> {
                alert.setStatus(AlertStatus.ESCALATED);
                alert.setReviewedBy(reviewedBy);
                alert.setReviewNotes(notes);
                alert.setReviewedAt(Instant.now());
                return alertRepository.save(alert);
            });
    }

    /**
     * Resolve alert.
     */
    @Transactional
    public Mono<FraudAlert> resolveAlert(UUID alertId, String reviewedBy, String notes) {
        return alertRepository.findById(alertId)
            .switchIfEmpty(Mono.error(new AlertNotFoundException(alertId)))
            .flatMap(alert -> {
                alert.setStatus(AlertStatus.RESOLVED);
                alert.setReviewedBy(reviewedBy);
                alert.setReviewNotes(notes);
                alert.setReviewedAt(Instant.now());
                return alertRepository.save(alert);
            });
    }

    /**
     * Get alert statistics.
     */
    public Mono<AlertStatistics> getStatistics() {
        Instant thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);

        return Mono.zip(
            alertRepository.countByStatus(AlertStatus.PENDING),
            alertRepository.countByStatus(AlertStatus.CONFIRMED_FRAUD),
            alertRepository.countByStatus(AlertStatus.FALSE_POSITIVE),
            alertRepository.countByRiskLevel(RiskLevel.CRITICAL),
            alertRepository.calculateFalsePositiveRate(thirtyDaysAgo).defaultIfEmpty(0.0)
        ).map(tuple -> AlertStatistics.builder()
            .pendingAlerts(tuple.getT1())
            .confirmedFraud(tuple.getT2())
            .falsePositives(tuple.getT3())
            .criticalAlerts(tuple.getT4())
            .falsePositiveRate(tuple.getT5())
            .build());
    }

    private RiskLevel convertRiskLevel(FraudCheckResponse.RiskLevel level) {
        return switch (level) {
            case LOW -> RiskLevel.LOW;
            case MEDIUM -> RiskLevel.MEDIUM;
            case HIGH -> RiskLevel.HIGH;
            case CRITICAL -> RiskLevel.CRITICAL;
        };
    }

    @lombok.Data
    @lombok.Builder
    public static class AlertStatistics {
        private long pendingAlerts;
        private long confirmedFraud;
        private long falsePositives;
        private long criticalAlerts;
        private double falsePositiveRate;
    }

    public static class AlertNotFoundException extends RuntimeException {
        public AlertNotFoundException(UUID alertId) {
            super("Alert not found: " + alertId);
        }
    }
}
