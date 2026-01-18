package com.bank.fraud.ml;

import com.bank.fraud.controller.FraudController.ModelHealthResponse;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for CatBoost ML model operations.
 * Handles model loading, inference, and health monitoring.
 */
@Slf4j
@Service
public class CatBoostModelService {

    @Value("${fraud.model.path:models/fraud_model.cbm}")
    private String modelPath;

    @Value("${fraud.model.version:1.0.0}")
    private String modelVersion;

    /**
     * CatBoost model instance.
     * In production, uncomment and add CatBoost JNI dependency:
     * private CatBoostModel model;
     */
    private boolean modelLoaded = false;
    private final AtomicLong totalPredictions = new AtomicLong(0);
    private final AtomicLong totalLatencyMs = new AtomicLong(0);

    @PostConstruct
    public void init() {
        loadModel();
    }

    /**
     * Load the CatBoost model from file.
     */
    private void loadModel() {
        try {
            log.info("Loading CatBoost model from: {}", modelPath);

            // Production: model = CatBoostModel.loadModel(modelPath);
            // Simulating model load for development environment
            modelLoaded = true;
            log.info("CatBoost model loaded successfully, version: {}", modelVersion);

        } catch (Exception e) {
            log.error("Failed to load CatBoost model", e);
            modelLoaded = false;
        }
    }

    /**
     * Run inference on extracted features.
     *
     * @param features the feature vector
     * @return the risk score prediction
     */
    public Mono<Double> predict(double[] features) {
        if (!modelLoaded) {
            log.warn("Model not loaded, returning default high risk score");
            return Mono.just(0.75); // Default to manual review
        }

        return Mono.fromCallable(() -> {
            long startTime = System.currentTimeMillis();

            // Production: double[] prediction = model.predict(features);
            // Production: double riskScore = prediction[0];
            // Simulated prediction for development environment
            double riskScore = simulatePrediction(features);

            long latency = System.currentTimeMillis() - startTime;
            totalPredictions.incrementAndGet();
            totalLatencyMs.addAndGet(latency);

            log.debug("Prediction completed in {}ms, score: {}", latency, riskScore);
            return riskScore;

        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Get model health status.
     */
    public Mono<ModelHealthResponse> getHealth() {
        return Mono.just(ModelHealthResponse.builder()
            .modelLoaded(modelLoaded)
            .modelVersion(modelVersion)
            .totalPredictions(totalPredictions.get())
            .averageLatencyMs(totalPredictions.get() > 0
                ? (double) totalLatencyMs.get() / totalPredictions.get()
                : 0.0)
            .build());
    }

    /**
     * Simulated prediction for development/testing.
     * Uses heuristic rules until CatBoost model is integrated in production.
     */
    private double simulatePrediction(double[] features) {
        // Simple heuristic based on features
        double amount = features.length > 0 ? features[0] : 0;
        double isExternal = features.length > 1 ? features[1] : 0;

        double baseScore = 0.1;

        // Higher amounts increase risk
        if (amount > 10000) {
            baseScore += 0.3;
        } else if (amount > 5000) {
            baseScore += 0.15;
        }

        // External transfers have higher risk
        if (isExternal > 0.5) {
            baseScore += 0.2;
        }

        // Add some randomness (simulating model uncertainty)
        baseScore += Math.random() * 0.1;

        return Math.min(baseScore, 1.0);
    }
}
