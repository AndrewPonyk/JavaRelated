package com.example.trading.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record Order(
        long id,
        String traderId,
        String symbol,
        OrderSide side,
        long quantity,
        long remainingQuantity,
        BigDecimal limitPrice,
        OrderStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public Order {
        if (id <= 0) {
            throw new IllegalArgumentException("id must be positive");
        }
        traderId = DomainValidation.identifier(traderId, "traderId");
        symbol = DomainValidation.symbol(symbol, "symbol");
        side = Objects.requireNonNull(side, "side");
        DomainValidation.quantity(quantity, "quantity");
        if (remainingQuantity < 0 || remainingQuantity > quantity) {
            throw new IllegalArgumentException("remainingQuantity must be between zero and quantity");
        }
        limitPrice = DomainValidation.money(limitPrice, "limitPrice", false);
        status = Objects.requireNonNull(status, "status");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not precede createdAt");
        }
        validateStatus(status, quantity, remainingQuantity);
    }

    public Order(
            long id,
            String traderId,
            String symbol,
            OrderSide side,
            long quantity,
            BigDecimal limitPrice,
            OrderStatus status,
            Instant createdAt) {
        this(id, traderId, symbol, side, quantity, initialRemaining(quantity, status),
                limitPrice, status, createdAt, createdAt);
    }

    public boolean isActive() {
        return status == OrderStatus.OPEN || status == OrderStatus.PARTIALLY_FILLED;
    }

    public long executedQuantity() {
        return quantity - remainingQuantity;
    }

    public Order fill(long fillQuantity, Instant at) {
        if (!isActive()) {
            throw new IllegalStateException("only active orders can be filled");
        }
        if (fillQuantity <= 0 || fillQuantity > remainingQuantity) {
            throw new IllegalArgumentException("fillQuantity exceeds the remaining quantity");
        }
        long remaining = remainingQuantity - fillQuantity;
        OrderStatus newStatus = remaining == 0 ? OrderStatus.FILLED : OrderStatus.PARTIALLY_FILLED;
        return new Order(
                id, traderId, symbol, side, quantity, remaining, limitPrice, newStatus, createdAt, at);
    }

    public Order cancel(Instant at) {
        if (!isActive()) {
            throw new IllegalStateException("only active orders can be cancelled");
        }
        return new Order(
                id, traderId, symbol, side, quantity, remainingQuantity, limitPrice,
                OrderStatus.CANCELLED, createdAt, at);
    }

    private static long initialRemaining(long quantity, OrderStatus status) {
        return status == OrderStatus.FILLED ? 0L : quantity;
    }

    private static void validateStatus(OrderStatus status, long quantity, long remaining) {
        boolean valid = switch (status) {
            case OPEN -> remaining == quantity;
            case PARTIALLY_FILLED -> remaining > 0 && remaining < quantity;
            case FILLED -> remaining == 0;
            case CANCELLED -> remaining >= 0;
        };
        if (!valid) {
            throw new IllegalArgumentException("status is inconsistent with remainingQuantity");
        }
    }

}
