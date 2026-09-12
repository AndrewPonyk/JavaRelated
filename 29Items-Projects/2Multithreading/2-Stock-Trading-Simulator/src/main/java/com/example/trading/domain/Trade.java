package com.example.trading.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record Trade(
        long id,
        long buyOrderId,
        long sellOrderId,
        String buyerTraderId,
        String sellerTraderId,
        String symbol,
        long quantity,
        BigDecimal executionPrice,
        Instant executedAt) {

    public Trade {
        if (id <= 0 || buyOrderId <= 0 || sellOrderId <= 0) {
            throw new IllegalArgumentException("trade and order IDs must be positive");
        }
        buyerTraderId = DomainValidation.identifier(buyerTraderId, "buyerTraderId");
        sellerTraderId = DomainValidation.identifier(sellerTraderId, "sellerTraderId");
        if (buyerTraderId.equals(sellerTraderId)) {
            throw new IllegalArgumentException("self trades are not allowed");
        }
        symbol = DomainValidation.symbol(symbol, "symbol");
        DomainValidation.quantity(quantity, "quantity");
        executionPrice = DomainValidation.money(executionPrice, "executionPrice", false);
        executedAt = Objects.requireNonNull(executedAt, "executedAt");
    }

    public BigDecimal notional() {
        return executionPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
