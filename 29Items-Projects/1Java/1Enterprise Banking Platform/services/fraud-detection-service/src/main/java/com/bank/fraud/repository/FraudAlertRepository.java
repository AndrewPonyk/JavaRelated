package com.bank.fraud.repository;

import com.bank.fraud.model.FraudAlert;
import com.bank.fraud.model.FraudAlert.AlertStatus;
import com.bank.fraud.model.FraudAlert.RiskLevel;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Reactive repository for FraudAlert entity.
 */
@Repository
public interface FraudAlertRepository extends R2dbcRepository<FraudAlert, UUID> {

    /**
     * Find alert by transaction ID.
     */
    Mono<FraudAlert> findByTransactionId(UUID transactionId);

    /**
     * Find alerts by account ID.
     */
    Flux<FraudAlert> findByAccountId(UUID accountId);

    /**
     * Find alerts by status.
     */
    Flux<FraudAlert> findByStatus(AlertStatus status);

    /**
     * Find pending alerts ordered by risk score (highest first).
     */
    @Query("SELECT * FROM fraud_alerts WHERE status = 'PENDING' ORDER BY risk_score DESC")
    Flux<FraudAlert> findPendingAlertsByRiskScore();

    /**
     * Find critical pending alerts.
     */
    @Query("SELECT * FROM fraud_alerts WHERE status = 'PENDING' AND risk_level = 'CRITICAL' ORDER BY created_at ASC")
    Flux<FraudAlert> findCriticalPendingAlerts();

    /**
     * Find alerts within a date range.
     */
    @Query("SELECT * FROM fraud_alerts WHERE created_at BETWEEN :startDate AND :endDate ORDER BY created_at DESC")
    Flux<FraudAlert> findByDateRange(Instant startDate, Instant endDate);

    /**
     * Count alerts by status.
     */
    Mono<Long> countByStatus(AlertStatus status);

    /**
     * Count alerts by risk level.
     */
    Mono<Long> countByRiskLevel(RiskLevel riskLevel);

    /**
     * Find false positive rate.
     */
    @Query("SELECT CAST(COUNT(CASE WHEN status = 'FALSE_POSITIVE' THEN 1 END) AS DOUBLE) / " +
           "NULLIF(COUNT(CASE WHEN status IN ('FALSE_POSITIVE', 'CONFIRMED_FRAUD') THEN 1 END), 0) " +
           "FROM fraud_alerts WHERE created_at >= :since")
    Mono<Double> calculateFalsePositiveRate(Instant since);
}
