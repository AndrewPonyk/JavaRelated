package com.shopflow.order.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/**
 * Inbound payload for creating an order. Bean Validation constraints are the
 * first line of defense; failures are mapped to a 400 with per-field detail by
 * the shared {@code GlobalExceptionHandler}.
 */
public record CreateOrderRequest(
        @NotBlank(message = "customerId is required")
        String customerId,

        @NotBlank
        @Pattern(regexp = "[A-Z]{3}", message = "currency must be an ISO-4217 code, e.g. EUR")
        String currency,

        @NotEmpty(message = "an order must contain at least one item")
        @Size(max = 100, message = "an order may contain at most 100 line items")
        @Valid
        List<Item> items) {

    /** A single requested line item. */
    public record Item(
            @NotBlank String productId,
            @Positive(message = "quantity must be positive") int quantity,
            @NotNull @DecimalMin(value = "0.0", inclusive = false, message = "unitPrice must be > 0")
            BigDecimal unitPrice) {
    }
}
