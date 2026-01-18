package com.bank.fraud.controller;

import com.bank.fraud.dto.FraudCheckRequest;
import com.bank.fraud.dto.FraudCheckResponse;
import com.bank.fraud.model.FraudAlert;
import com.bank.fraud.service.FraudAlertService;
import com.bank.fraud.service.FraudDetectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * REST controller for fraud detection operations.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/fraud")
@RequiredArgsConstructor
@Tag(name = "Fraud Detection", description = "ML-based fraud detection APIs")
public class FraudController {

    private final FraudDetectionService fraudDetectionService;
    private final FraudAlertService alertService;

    @PostMapping("/check")
    @Operation(summary = "Check transaction for fraud",
               description = "Analyzes a transaction using ML model and returns risk score")
    public Mono<FraudCheckResponse> checkFraud(
            @Valid @RequestBody FraudCheckRequest request) {
        log.info("Fraud check requested for transaction: {}", request.getTransactionId());
        return fraudDetectionService.analyzeTransaction(request);
    }

    @GetMapping("/health")
    @Operation(summary = "Model health check",
               description = "Returns the status of the ML model")
    public Mono<ModelHealthResponse> getModelHealth() {
        return fraudDetectionService.getModelHealth();
    }

    // ==================== Alert Management ====================

    @GetMapping("/alerts/{alertId}")
    @Operation(summary = "Get fraud alert",
               description = "Retrieves a fraud alert by ID")
    public Mono<FraudAlert> getAlert(@PathVariable UUID alertId) {
        log.debug("Fetching alert: {}", alertId);
        return alertService.getAlert(alertId);
    }

    @GetMapping(value = "/alerts/pending", produces = MediaType.APPLICATION_NDJSON_VALUE)
    @Operation(summary = "Get pending alerts",
               description = "Retrieves all pending fraud alerts ordered by risk score")
    public Flux<FraudAlert> getPendingAlerts() {
        log.debug("Fetching pending alerts");
        return alertService.getPendingAlerts();
    }

    @GetMapping(value = "/alerts/critical", produces = MediaType.APPLICATION_NDJSON_VALUE)
    @Operation(summary = "Get critical alerts",
               description = "Retrieves all critical pending fraud alerts")
    public Flux<FraudAlert> getCriticalAlerts() {
        log.debug("Fetching critical alerts");
        return alertService.getCriticalAlerts();
    }

    @GetMapping(value = "/alerts/account/{accountId}", produces = MediaType.APPLICATION_NDJSON_VALUE)
    @Operation(summary = "Get alerts by account",
               description = "Retrieves all fraud alerts for an account")
    public Flux<FraudAlert> getAlertsByAccount(@PathVariable UUID accountId) {
        log.debug("Fetching alerts for account: {}", accountId);
        return alertService.getAlertsByAccount(accountId);
    }

    @PostMapping("/alerts/{alertId}/confirm")
    @Operation(summary = "Confirm fraud",
               description = "Marks an alert as confirmed fraud")
    public Mono<FraudAlert> confirmFraud(
            @PathVariable UUID alertId,
            @RequestParam @NotBlank @Size(max = 100) String reviewedBy,
            @RequestParam(required = false) @Size(max = 1000) String notes) {
        log.info("Confirming fraud for alert: {}", alertId);
        return alertService.confirmFraud(alertId, reviewedBy, notes);
    }

    @PostMapping("/alerts/{alertId}/false-positive")
    @Operation(summary = "Mark false positive",
               description = "Marks an alert as false positive")
    public Mono<FraudAlert> markFalsePositive(
            @PathVariable UUID alertId,
            @RequestParam @NotBlank @Size(max = 100) String reviewedBy,
            @RequestParam(required = false) @Size(max = 1000) String notes) {
        log.info("Marking false positive for alert: {}", alertId);
        return alertService.markFalsePositive(alertId, reviewedBy, notes);
    }

    @PostMapping("/alerts/{alertId}/escalate")
    @Operation(summary = "Escalate alert",
               description = "Escalates an alert for further investigation")
    public Mono<FraudAlert> escalateAlert(
            @PathVariable UUID alertId,
            @RequestParam @NotBlank @Size(max = 100) String reviewedBy,
            @RequestParam(required = false) @Size(max = 1000) String notes) {
        log.info("Escalating alert: {}", alertId);
        return alertService.escalateAlert(alertId, reviewedBy, notes);
    }

    @PostMapping("/alerts/{alertId}/resolve")
    @Operation(summary = "Resolve alert",
               description = "Resolves an alert")
    public Mono<FraudAlert> resolveAlert(
            @PathVariable UUID alertId,
            @RequestParam @NotBlank @Size(max = 100) String reviewedBy,
            @RequestParam(required = false) @Size(max = 1000) String notes) {
        log.info("Resolving alert: {}", alertId);
        return alertService.resolveAlert(alertId, reviewedBy, notes);
    }

    @GetMapping("/alerts/stats")
    @Operation(summary = "Get alert statistics",
               description = "Retrieves fraud alert statistics")
    public Mono<FraudAlertService.AlertStatistics> getAlertStatistics() {
        log.debug("Fetching alert statistics");
        return alertService.getStatistics();
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ModelHealthResponse {
        private boolean modelLoaded;
        private String modelVersion;
        private long totalPredictions;
        private double averageLatencyMs;
    }
}
