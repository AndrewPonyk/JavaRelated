package com.example.orderservice.api.dto;

import com.example.orderservice.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Lightweight projection for list endpoints — intentionally excludes items to keep
 * list queries single-table (no N+1; see ARCHITECTURE.md §2.3).
 */
public record OrderSummaryResponse(
        UUID id,
        UUID customerId,
        OrderStatus status,
        String currency,
        BigDecimal totalAmount,
        Instant createdAt) {
}
