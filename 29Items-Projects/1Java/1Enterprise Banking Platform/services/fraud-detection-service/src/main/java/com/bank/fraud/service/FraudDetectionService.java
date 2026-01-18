package com.bank.fraud.service;

import com.bank.fraud.controller.FraudController.ModelHealthResponse;
import com.bank.fraud.dto.FraudCheckRequest;
import com.bank.fraud.dto.FraudCheckResponse;
import com.bank.fraud.dto.FraudCheckResponse.RecommendedAction;
import com.bank.fraud.dto.FraudCheckResponse.RiskLevel;
import com.bank.fraud.ml.CatBoostModelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for fraud detection using ML model.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FraudDetectionService {

    private final CatBoostModelService modelService;
    private final FeatureEngineeringService featureService;
    private final FraudAlertService alertService;

    // Risk thresholds
    private static final BigDecimal HIGH_AMOUNT_THRESHOLD = new BigDecimal("10000");
    private static final BigDecimal VERY_HIGH_AMOUNT_THRESHOLD = new BigDecimal("50000");
    private static final int UNUSUAL_HOUR_START = 1;  // 1 AM
    private static final int UNUSUAL_HOUR_END = 5;    // 5 AM

    /**
     * Analyze a transaction for fraud.
     */
    public Mono<FraudCheckResponse> analyzeTransaction(FraudCheckRequest request) {
        log.debug("Analyzing transaction: {}", request.getTransactionId());
        long startTime = System.currentTimeMillis();

        return featureService.extractFeatures(request)
            .flatMap(features -> modelService.predict(features))
            .flatMap(riskScore -> {
                long inferenceTime = System.currentTimeMillis() - startTime;

                List<String> riskFactors = analyzeRiskFactors(request, riskScore);
                RiskLevel riskLevel = determineRiskLevel(riskScore);
                RecommendedAction action = determineAction(riskScore, riskFactors);

                log.info("Transaction {} scored: {} ({}), action: {}",
                    request.getTransactionId(), riskScore, riskLevel, action);

                FraudCheckResponse response = FraudCheckResponse.builder()
                    .transactionId(request.getTransactionId())
                    .riskScore(riskScore)
                    .riskLevel(riskLevel)
                    .riskFactors(riskFactors)
                    .recommendedAction(action)
                    .inferenceTimeMs(inferenceTime)
                    .checkedAt(Instant.now())
                    .build();

                // Create alert for non-low risk transactions
                if (riskLevel != RiskLevel.LOW) {
                    return alertService.createAlert(request, response)
                        .thenReturn(response)
                        .onErrorResume(e -> {
                            log.warn("Failed to create fraud alert: {}", e.getMessage());
                            return Mono.just(response);
                        });
                }

                // Update velocity counters
                return featureService.updateVelocity(
                        request.getSourceAccountId().toString(),
                        request.getAmount().doubleValue()
                    )
                    .thenReturn(response)
                    .onErrorReturn(response);
            });
    }

    /**
     * Get model health status.
     */
    public Mono<ModelHealthResponse> getModelHealth() {
        return modelService.getHealth();
    }

    private List<String> analyzeRiskFactors(FraudCheckRequest request, double riskScore) {
        List<String> factors = new ArrayList<>();

        // Amount analysis
        if (request.getAmount().compareTo(VERY_HIGH_AMOUNT_THRESHOLD) > 0) {
            factors.add("VERY_HIGH_AMOUNT");
        } else if (request.getAmount().compareTo(HIGH_AMOUNT_THRESHOLD) > 0) {
            factors.add("HIGH_AMOUNT");
        }

        // Transaction type analysis
        if ("EXTERNAL_TRANSFER".equals(request.getTransactionType())) {
            factors.add("EXTERNAL_TRANSFER");
        } else if ("INTERNATIONAL_TRANSFER".equals(request.getTransactionType())) {
            factors.add("INTERNATIONAL_TRANSFER");
        }

        // Time-based analysis
        int currentHour = LocalTime.now().getHour();
        if (currentHour >= UNUSUAL_HOUR_START && currentHour <= UNUSUAL_HOUR_END) {
            factors.add("UNUSUAL_TIME");
        }

        // Weekend transactions (different risk profile)
        int dayOfWeek = java.time.LocalDate.now().getDayOfWeek().getValue();
        if (dayOfWeek >= 6) { // Saturday or Sunday
            factors.add("WEEKEND_TRANSACTION");
        }

        // IP address analysis
        if (request.getSourceIpAddress() != null) {
            if (isHighRiskIp(request.getSourceIpAddress())) {
                factors.add("HIGH_RISK_IP");
            }
            if (isVpnOrProxy(request.getSourceIpAddress())) {
                factors.add("VPN_OR_PROXY_DETECTED");
            }
        }

        // Device analysis
        if (request.getDeviceId() == null || request.getDeviceId().isEmpty()) {
            factors.add("UNKNOWN_DEVICE");
        }

        // Location analysis
        if (request.getLatitude() != null && request.getLongitude() != null) {
            if (isHighRiskLocation(request.getLatitude(), request.getLongitude())) {
                factors.add("HIGH_RISK_LOCATION");
            }
        }

        // ML model flag
        if (riskScore > 0.5) {
            factors.add("ML_MODEL_FLAG");
        }

        return factors;
    }

    private boolean isHighRiskIp(String ipAddress) {
        // Check against known high-risk IP ranges
        // In production, use IP reputation services like MaxMind, AbuseIPDB
        return ipAddress.startsWith("10.") || ipAddress.startsWith("192.168.");
    }

    private boolean isVpnOrProxy(String ipAddress) {
        // Detect VPN/Proxy usage
        // In production, integrate with IP intelligence services
        return false; // Placeholder
    }

    private boolean isHighRiskLocation(Double latitude, Double longitude) {
        // Check against high-risk geographic regions
        // In production, use geo-fencing services
        return false; // Placeholder
    }

    private RiskLevel determineRiskLevel(double riskScore) {
        if (riskScore < 0.3) return RiskLevel.LOW;
        if (riskScore < 0.6) return RiskLevel.MEDIUM;
        if (riskScore < 0.8) return RiskLevel.HIGH;
        return RiskLevel.CRITICAL;
    }

    private RecommendedAction determineAction(double riskScore, List<String> riskFactors) {
        if (riskScore >= 0.9) {
            return RecommendedAction.BLOCK;
        }
        if (riskScore >= 0.7) {
            return RecommendedAction.REQUIRE_2FA;
        }
        if (riskScore >= 0.5 || riskFactors.size() > 2) {
            return RecommendedAction.REVIEW;
        }
        return RecommendedAction.ALLOW;
    }
}
