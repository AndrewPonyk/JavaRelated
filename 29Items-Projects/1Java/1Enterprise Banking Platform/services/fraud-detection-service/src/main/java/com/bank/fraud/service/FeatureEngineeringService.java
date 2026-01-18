package com.bank.fraud.service;

import com.bank.fraud.dto.FraudCheckRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Service for feature engineering and extraction.
 * Transforms raw transaction data into ML model features.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeatureEngineeringService {

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    private static final Duration VELOCITY_WINDOW = Duration.ofHours(24);

    /**
     * Extract features from a fraud check request.
     *
     * @param request the fraud check request
     * @return array of features for ML model
     */
    public Mono<double[]> extractFeatures(FraudCheckRequest request) {
        log.debug("Extracting features for transaction: {}", request.getTransactionId());

        return getVelocityFeatures(request.getSourceAccountId().toString())
            .map(velocityFeatures -> {
                // Feature vector layout:
                // [0] - Transaction amount (normalized)
                // [1] - Is external transfer (0 or 1)
                // [2] - Hour of day (0-23)
                // [3] - Day of week (0-6)
                // [4] - Transaction count in last 24h
                // [5] - Amount sum in last 24h
                // [6] - Is new recipient (0 or 1)
                // [7] - Distance from usual location

                double[] features = new double[8];

                // Amount (log-normalized)
                features[0] = Math.log1p(request.getAmount().doubleValue());

                // Transaction type
                features[1] = "EXTERNAL_TRANSFER".equals(request.getTransactionType()) ? 1.0 : 0.0;

                // Time features
                int hour = java.time.LocalTime.now().getHour();
                int dayOfWeek = java.time.LocalDate.now().getDayOfWeek().getValue();
                features[2] = hour / 24.0; // Normalize to 0-1
                features[3] = dayOfWeek / 7.0;

                // Velocity features from cache
                features[4] = velocityFeatures[0]; // Transaction count
                features[5] = velocityFeatures[1]; // Amount sum

                // Recipient analysis: requires historical recipient data integration
                // Currently defaults to 0 (known recipient) until recipient service is available
                features[6] = 0.0;

                // Geo analysis: requires device location data integration
                // Currently defaults to 0 (no distance anomaly) until location service is available
                features[7] = 0.0;

                return features;
            });
    }

    /**
     * Get velocity features from Redis cache.
     */
    private Mono<double[]> getVelocityFeatures(String accountId) {
        String countKey = "velocity:count:" + accountId;
        String amountKey = "velocity:amount:" + accountId;

        return Mono.zip(
            redisTemplate.opsForValue().get(countKey).defaultIfEmpty("0"),
            redisTemplate.opsForValue().get(amountKey).defaultIfEmpty("0")
        ).map(tuple -> {
            double count = Double.parseDouble(tuple.getT1());
            double amount = Double.parseDouble(tuple.getT2());

            // Normalize
            return new double[] {
                Math.min(count / 100.0, 1.0),  // Assume max 100 txns/day
                Math.log1p(amount) / 15.0       // Log normalize amount
            };
        }).onErrorReturn(new double[] {0.0, 0.0});
    }

    /**
     * Update velocity counters after transaction.
     */
    public Mono<Void> updateVelocity(String accountId, double amount) {
        String countKey = "velocity:count:" + accountId;
        String amountKey = "velocity:amount:" + accountId;

        return Mono.when(
            redisTemplate.opsForValue().increment(countKey)
                .flatMap(v -> redisTemplate.expire(countKey, VELOCITY_WINDOW)),
            redisTemplate.opsForValue().increment(amountKey, amount)
                .flatMap(v -> redisTemplate.expire(amountKey, VELOCITY_WINDOW))
        );
    }
}
