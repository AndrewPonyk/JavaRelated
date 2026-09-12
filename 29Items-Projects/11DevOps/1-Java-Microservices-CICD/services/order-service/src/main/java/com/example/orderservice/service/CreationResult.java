package com.example.orderservice.service;

import com.example.orderservice.api.dto.OrderResponse;

/**
 * Outcome of an order-creation attempt. {@code replayed} is true when an
 * Idempotency-Key matched an existing order — the web layer then answers
 * 200 OK instead of 201 Created.
 */
public record CreationResult(OrderResponse order, boolean replayed) {
}
