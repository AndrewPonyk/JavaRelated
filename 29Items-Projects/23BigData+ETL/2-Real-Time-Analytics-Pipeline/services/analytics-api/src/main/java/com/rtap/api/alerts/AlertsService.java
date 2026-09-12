package com.rtap.api.alerts;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * Alert workflow: list, acknowledge, and Kafka ingestion into the durable history.
 * Status transitions are decided by the database (optimistic UPDATE … WHERE
 * status='open'), so concurrent acks are safe and double-ack keeps the first acker.
 */
@Service
public class AlertsService {

    private final AnomalyAlertRepository repository;

    public AlertsService(AnomalyAlertRepository repository) {
        this.repository = repository;
    }

    public List<AnomalyAlertDto> list(String status, int limit) {
        return repository.list(status, limit);
    }

    public AnomalyAlertDto acknowledge(String alertId, String user) {
        repository.acknowledge(alertId, user); // no-op when not open (idempotent)
        return repository.find(alertId)
                .orElseThrow(() -> new NoSuchElementException("Unknown alert: " + alertId));
    }

    /** @return true when the alert is new (first delivery) — drives SSE fan-out */
    public boolean ingest(AlertMessage alert) {
        return repository.insert(alert);
    }
}
