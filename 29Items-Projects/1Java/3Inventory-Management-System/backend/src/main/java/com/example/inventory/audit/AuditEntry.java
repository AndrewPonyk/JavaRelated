package com.example.inventory.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "audit_entries")
public class AuditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "entity_type", nullable = false, length = 80)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(nullable = false, length = 80)
    private String action;

    @Column(nullable = false, length = 160)
    private String actor;

    @Column(nullable = false, length = 250)
    private String reason;

    @Column(name = "correlation_id", nullable = false, length = 128)
    private String correlationId;

    @Column(name = "before_state", columnDefinition = "json")
    private String beforeState;

    @Column(name = "after_state", columnDefinition = "json")
    private String afterState;

    @CreationTimestamp
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected AuditEntry() {
    }

    public AuditEntry(String entityType, UUID entityId, String action, String actor, String reason,
                      String correlationId, String beforeState, String afterState) {
        this.entityType = entityType;
        this.entityId = entityId;
        this.action = action;
        this.actor = actor;
        this.reason = reason;
        this.correlationId = correlationId;
        this.beforeState = beforeState;
        this.afterState = afterState;
    }

    public UUID getId() { return id; }
    public String getEntityType() { return entityType; }
    public UUID getEntityId() { return entityId; }
    public String getAction() { return action; }
    public String getActor() { return actor; }
    public String getReason() { return reason; }
    public String getCorrelationId() { return correlationId; }
    public String getBeforeState() { return beforeState; }
    public String getAfterState() { return afterState; }
    public Instant getOccurredAt() { return occurredAt; }
}

