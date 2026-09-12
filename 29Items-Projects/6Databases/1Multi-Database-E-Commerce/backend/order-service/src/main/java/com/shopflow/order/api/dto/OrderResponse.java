package com.shopflow.order.api.dto;

import com.shopflow.order.domain.Order;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Outbound representation of an order. Decoupled from the JPA entity so the
 * persistence model can evolve without breaking the API contract.
 */
public record OrderResponse(
        String id,
        String customerId,
        String status,
        String currency,
        BigDecimal totalAmount,
        Instant createdAt,
        List<Line> items) {

    public record Line(String productId, int quantity, BigDecimal unitPrice, BigDecimal lineTotal) { }

    /** Map a domain {@link Order} to its API representation. */
    public static OrderResponse from(Order order) {
        List<Line> lines = order.getItems().stream()
                .map(i -> new Line(i.getProductId(), i.getQuantity(), i.getUnitPrice(), i.lineTotal()))
                .toList();
        return new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getStatus().name(),
                order.getCurrency(),
                order.getTotalAmount(),
                order.getCreatedAt(),
                lines);
    }
}
