package com.rtap.api.metrics;

import com.rtap.api.metrics.dto.AggregatePoint;
import com.rtap.api.metrics.dto.CreateMetricRequest;
import com.rtap.api.metrics.dto.MetricDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Read-path service (CQRS query side — ARCHITECTURE.md §2.1).
 *
 * <p>Routing rule for aggregate queries: ranges ending inside the configured hot
 * window are served from Elasticsearch (fast bucket aggregations over the freshest
 * data); historical ranges — and any ES failure — fall back to PostgreSQL, which is
 * the durable source of truth. Dimension series are merged store-side either way.
 */
@Service
public class MetricsService {

    private static final Logger LOG = LoggerFactory.getLogger(MetricsService.class);
    private static final Duration DEFAULT_RANGE = Duration.ofMinutes(15);
    private static final long MAX_POINTS = 5_000;

    private final MetricDefinitionRepository definitions;
    private final MetricAggregateRepository aggregates;
    private final ElasticsearchAggregatesClient elasticsearch;

    public MetricsService(MetricDefinitionRepository definitions,
                          MetricAggregateRepository aggregates,
                          ElasticsearchAggregatesClient elasticsearch) {
        this.definitions = definitions;
        this.aggregates = aggregates;
        this.elasticsearch = elasticsearch;
    }

    public List<MetricDefinition> listDefinitions() {
        return definitions.findAll();
    }

    public MetricDefinition create(CreateMetricRequest request) {
        MetricDefinition definition = new MetricDefinition(
                request.metricKey(), request.displayName(), request.unit(), request.description(), Instant.now());
        if (!definitions.insert(definition)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Metric '%s' already exists".formatted(request.metricKey()));
        }
        return definition;
    }

    public List<AggregatePoint> queryAggregates(String metricKey, String window, Instant from, Instant to) {
        if (definitions.find(metricKey).isEmpty()) {
            throw new NoSuchElementException("Unknown metric: " + metricKey);
        }
        WindowSpec spec = WindowSpec.parse(window);

        Instant effectiveTo = to != null ? to : Instant.now();
        Instant effectiveFrom = from != null ? from : effectiveTo.minus(DEFAULT_RANGE);
        if (!effectiveFrom.isBefore(effectiveTo)) {
            throw new IllegalArgumentException("'from' must be before 'to'");
        }
        long points = Duration.between(effectiveFrom, effectiveTo).toMillis() / spec.millis();
        if (points > MAX_POINTS) {
            throw new IllegalArgumentException(
                    "Range would produce %d points (max %d) — use a coarser window".formatted(points, MAX_POINTS));
        }

        if (elasticsearch.isEnabled()
                && effectiveTo.isAfter(Instant.now().minus(elasticsearch.hotWindow()))) {
            try {
                return elasticsearch.query(metricKey, spec, effectiveFrom, effectiveTo);
            } catch (Exception e) {
                LOG.warn("Elasticsearch hot-path query failed ({}); falling back to PostgreSQL",
                        e.getMessage());
            }
        }
        return aggregates.query(metricKey, spec, effectiveFrom, effectiveTo);
    }
}
