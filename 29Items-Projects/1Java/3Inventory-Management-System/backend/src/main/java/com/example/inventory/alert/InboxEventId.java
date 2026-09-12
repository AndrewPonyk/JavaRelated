package com.example.inventory.alert;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class InboxEventId implements Serializable {

    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "consumer_name", length = 120)
    private String consumerName;

    protected InboxEventId() {
    }

    public InboxEventId(UUID eventId, String consumerName) {
        this.eventId = eventId;
        this.consumerName = consumerName;
    }

    public UUID getEventId() { return eventId; }
    public String getConsumerName() { return consumerName; }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof InboxEventId that)) return false;
        return Objects.equals(eventId, that.eventId) && Objects.equals(consumerName, that.consumerName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId, consumerName);
    }
}

