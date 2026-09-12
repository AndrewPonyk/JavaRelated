package com.example.orderservice.exception;

import java.util.UUID;

/** Raised when an order id does not exist; mapped to HTTP 404 by the web layer. */
public class OrderNotFoundException extends RuntimeException {

    private final UUID orderId;

    public OrderNotFoundException(UUID orderId) {
        super("Order %s was not found".formatted(orderId));
        this.orderId = orderId;
    }

    public UUID getOrderId() {
        return orderId;
    }
}
