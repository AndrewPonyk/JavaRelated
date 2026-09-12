package com.example.orderservice.api.dto;

import com.example.orderservice.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Full order representation, including lines. */
public record OrderResponse(
        UUID id,
        UUID customerId,
        OrderStatus status,
        String currency,
        BigDecimal totalAmount,
        Instant createdAt,
        Instant updatedAt,
        List<OrderItemResponse> items) {
}
