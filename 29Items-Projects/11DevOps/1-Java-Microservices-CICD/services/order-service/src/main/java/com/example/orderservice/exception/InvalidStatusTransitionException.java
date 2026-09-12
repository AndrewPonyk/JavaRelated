package com.example.orderservice.exception;

import com.example.orderservice.domain.OrderStatus;
import java.util.UUID;

/** Raised by the domain when a lifecycle move violates the state machine; mapped to HTTP 409. */
public class InvalidStatusTransitionException extends RuntimeException {

    private final UUID orderId;
    private final OrderStatus from;
    private final OrderStatus to;

    public InvalidStatusTransitionException(UUID orderId, OrderStatus from, OrderStatus to) {
        super("Order %s cannot transition from %s to %s".formatted(orderId, from, to));
        this.orderId = orderId;
        this.from = from;
        this.to = to;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public OrderStatus getFrom() {
        return from;
    }

    public OrderStatus getTo() {
        return to;
    }
}
