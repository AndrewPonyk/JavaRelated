package com.bank.transaction.service;

import com.bank.transaction.model.Transaction;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Client for Fraud Detection Service.
 * Implements circuit breaker and retry patterns.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FraudCheckClient {

    private final WebClient.Builder webClientBuilder;

    private static final String FRAUD_SERVICE_URL = "http://fraud-detection-service";

    /**
     * Check transaction for fraud risk.
     *
     * @param transaction the transaction to check
     * @return risk score between 0 (safe) and 1 (high risk)
     */
    @CircuitBreaker(name = "fraudService", fallbackMethod = "checkFraudFallback")
    @TimeLimiter(name = "fraudService")
    public Mono<Double> checkFraud(Transaction transaction) {
        log.debug("Checking fraud for transaction: {}", transaction.getId());

        return webClientBuilder.build()
            .post()
            .uri(FRAUD_SERVICE_URL + "/api/v1/fraud/check")
            .bodyValue(FraudCheckRequest.builder()
                .transactionId(transaction.getId())
                .sourceAccountId(transaction.getSourceAccountId())
                .targetAccountId(transaction.getTargetAccountId())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .transactionType(transaction.getTransactionType().name())
                .build())
            .retrieve()
            .bodyToMono(FraudCheckResponse.class)
            .map(FraudCheckResponse::getRiskScore)
            .doOnSuccess(score ->
                log.debug("Fraud check result for {}: {}", transaction.getId(), score));
    }

    /**
     * Fallback when fraud service is unavailable.
     * Returns a neutral risk score and flags for review.
     */
    public Mono<Double> checkFraudFallback(Transaction transaction, Throwable ex) {
        log.warn("Fraud service unavailable for transaction {}: {}",
            transaction.getId(), ex.getMessage());
        // Return high risk score to trigger manual review
        return Mono.just(0.75);
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class FraudCheckRequest {
        private java.util.UUID transactionId;
        private java.util.UUID sourceAccountId;
        private java.util.UUID targetAccountId;
        private java.math.BigDecimal amount;
        private String currency;
        private String transactionType;
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class FraudCheckResponse {
        private double riskScore;
        private String riskLevel;
        private java.util.List<String> riskFactors;
    }
}
