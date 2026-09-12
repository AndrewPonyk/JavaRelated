package com.rtap.streaming.common.model;

import java.io.Serializable;

/**
 * Union of a successfully deserialized {@link BusinessEvent} and a {@link DeadLetter}.
 * The source emits this wrapper so poison pills stay in-band until the router splits
 * them onto the DLQ side output — nothing is ever dropped silently.
 */
public class ConsumedEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private BusinessEvent event;     // exactly one of the two is set
    private DeadLetter deadLetter;

    public ConsumedEvent() {
    }

    public static ConsumedEvent of(BusinessEvent event) {
        ConsumedEvent c = new ConsumedEvent();
        c.event = event;
        return c;
    }

    public static ConsumedEvent deadLetter(DeadLetter deadLetter) {
        ConsumedEvent c = new ConsumedEvent();
        c.deadLetter = deadLetter;
        return c;
    }

    public boolean isDeadLetter() {
        return deadLetter != null;
    }

    /** Timestamp used for watermarking: event time when valid, receive time otherwise. */
    public long timestamp() {
        return event != null ? event.getOccurredAt() : deadLetter.getReceivedAt();
    }

    public BusinessEvent getEvent() { return event; }
    public void setEvent(BusinessEvent event) { this.event = event; }

    public DeadLetter getDeadLetter() { return deadLetter; }
    public void setDeadLetter(DeadLetter deadLetter) { this.deadLetter = deadLetter; }
}
