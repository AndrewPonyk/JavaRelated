package com.bank.account.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Base interface for all account domain events.
 */
public interface AccountEvent {

    /**
     * Get the event type identifier.
     */
    String getEventType();

    /**
     * Get the aggregate (account) ID this event belongs to.
     */
    UUID getAggregateId();

    /**
     * Get the timestamp when this event occurred.
     */
    Instant getTimestamp();
}
