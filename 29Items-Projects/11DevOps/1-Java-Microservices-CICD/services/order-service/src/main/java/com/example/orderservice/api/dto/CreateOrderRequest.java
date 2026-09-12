package com.example.orderservice.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Payload for {@code POST /api/v1/orders}. Currency is optional and defaults to USD. */
public record CreateOrderRequest(
        @NotNull UUID customerId,
        @Pattern(regexp = "[A-Za-z]{3}", message = "currency must be a 3-letter ISO 4217 code") String currency,
        @NotEmpty(message = "an order needs at least one item")
        @Size(max = 100, message = "an order cannot have more than 100 items")
        @Valid List<OrderItemRequest> items) {
}
