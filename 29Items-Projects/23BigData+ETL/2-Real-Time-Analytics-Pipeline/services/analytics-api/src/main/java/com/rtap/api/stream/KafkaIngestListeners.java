package com.rtap.api.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rtap.api.alerts.AlertMessage;
import com.rtap.api.alerts.AlertsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka → API bridge. Both listeners read with {@code isolation.level=read_committed}
 * (application.yml) — the upstream sinks are transactional and reading uncommitted
 * data would break exactly-once at the last hop (TECH-NOTES §3.6.2).
 *
 * <p>Aggregates are fan-out only (Flink owns their persistence); alerts are ingested
 * into PostgreSQL idempotently (alertId PK) and fanned out on first insert only, so
 * redeliveries never double-toast the UI.
 */
@Component
@ConditionalOnProperty(name = "rtap.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaIngestListeners {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaIngestListeners.class);

    private final SseBroadcaster broadcaster;
    private final AlertsService alertsService;
    private final ObjectMapper mapper;

    public KafkaIngestListeners(SseBroadcaster broadcaster, AlertsService alertsService, ObjectMapper mapper) {
        this.broadcaster = broadcaster;
        this.alertsService = alertsService;
        this.mapper = mapper;
    }

    @KafkaListener(topics = "${rtap.topics.aggregates:metrics.aggregates.v1}")
    public void onAggregate(String json) {
        broadcaster.broadcast("aggregate", json);
    }

    @KafkaListener(topics = "${rtap.topics.alerts:alerts.anomalies.v1}")
    public void onAlert(String json) {
        try {
            AlertMessage alert = mapper.readValue(json, AlertMessage.class);
            boolean firstDelivery = alertsService.ingest(alert);
            if (firstDelivery) {
                broadcaster.broadcast("alert", json);
            }
        } catch (Exception e) {
            // The stream must keep flowing; a malformed alert is logged, not fatal.
            LOG.warn("Skipping unprocessable alert message ({} chars): {}", json.length(), e.getMessage());
        }
    }
}
