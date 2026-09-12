package com.example.inventory.alert;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "inbox_events")
public class InboxEvent {

    @EmbeddedId
    private InboxEventId id;

    @CreationTimestamp
    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    protected InboxEvent() {
    }

    public InboxEvent(InboxEventId id) {
        this.id = id;
    }

    public InboxEventId getId() { return id; }
    public Instant getProcessedAt() { return processedAt; }
}

