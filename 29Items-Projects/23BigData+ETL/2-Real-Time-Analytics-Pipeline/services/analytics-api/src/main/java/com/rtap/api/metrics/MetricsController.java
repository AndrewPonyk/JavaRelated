package com.rtap.api.metrics;

import com.rtap.api.metrics.dto.AggregatePoint;
import com.rtap.api.metrics.dto.CreateMetricRequest;
import com.rtap.api.metrics.dto.MetricDefinition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.util.List;

/**
 * Metric definitions (CRUD) and aggregate time-series queries (read path).
 * Validation happens here at the edge; business rules live in {@link MetricsService}.
 *
 * <p>AuthN/Z (Cognito JWT: viewer role for GETs, admin for POST) ships with the AWS
 * deployment — PROJECT-PLAN Phase 2, AWS-gated (ARCHITECTURE.md §2.5).
 */
@RestController
@RequestMapping("/api/v1/metrics")
@Validated
public class MetricsController {

    private final MetricsService metricsService;

    public MetricsController(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    /** List all registered metric definitions. */
    @GetMapping
    public List<MetricDefinition> listDefinitions() {
        return metricsService.listDefinitions();
    }

    /** Register a new metric definition. */
    @PostMapping
    public ResponseEntity<MetricDefinition> create(@Valid @RequestBody CreateMetricRequest request) {
        MetricDefinition created = metricsService.create(request);
        return ResponseEntity
                .created(URI.create("/api/v1/metrics/" + created.metricKey()))
                .body(created);
    }

    /**
     * Windowed aggregates for one metric, e.g.
     * {@code GET /api/v1/metrics/orders.completed/aggregates?window=1m&from=…&to=…}.
     * Defaults to the last 15 minutes at 1m resolution.
     */
    @GetMapping("/{metricKey}/aggregates")
    public List<AggregatePoint> aggregates(
            @PathVariable String metricKey,
            @RequestParam(defaultValue = "1m")
            @Pattern(regexp = "1s|10s|1m|5m|1h", message = "window must be one of: 1s, 10s, 1m, 5m, 1h")
            String window,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return metricsService.queryAggregates(metricKey, window, from, to);
    }
}
