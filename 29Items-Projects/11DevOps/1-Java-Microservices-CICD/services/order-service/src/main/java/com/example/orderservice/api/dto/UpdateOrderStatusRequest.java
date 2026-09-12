package com.example.orderservice.api.dto;

import com.example.orderservice.domain.OrderStatus;
import jakarta.validation.constraints.NotNull;

/** Payload for {@code PATCH /api/v1/orders/{id}/status}. */
public record UpdateOrderStatusRequest(@NotNull OrderStatus status) {
}
