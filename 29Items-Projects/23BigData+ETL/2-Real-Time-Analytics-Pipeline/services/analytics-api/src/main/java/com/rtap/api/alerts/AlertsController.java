package com.rtap.api.alerts;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Anomaly alert workflow: list and acknowledge.
 *
 * <p>AuthN/Z is deliberately absent here: it ships with the AWS deployment
 * (Cognito resource-server, PROJECT-PLAN Phase 2 — AWS-gated). Until then
 * {@code ackedBy} records a fixed local principal.
 */
@RestController
@RequestMapping("/api/v1/alerts")
@Validated
public class AlertsController {

    static final String LOCAL_PRINCIPAL = "local-operator";

    private final AlertsService alertsService;

    public AlertsController(AlertsService alertsService) {
        this.alertsService = alertsService;
    }

    /** List alerts, newest first. {@code ?status=open|acknowledged|resolved|all&limit=1..500} */
    @GetMapping
    public List<AnomalyAlertDto> list(
            @RequestParam(defaultValue = "open")
            @Pattern(regexp = "open|acknowledged|resolved|all") String status,
            @RequestParam(defaultValue = "100") @Min(1) @Max(500) int limit) {
        return alertsService.list(status, limit);
    }

    /** Acknowledge an alert — idempotent (double-ack keeps the first acker). */
    @PostMapping("/{alertId}/ack")
    public AnomalyAlertDto acknowledge(@PathVariable String alertId) {
        return alertsService.acknowledge(alertId, LOCAL_PRINCIPAL);
    }
}
