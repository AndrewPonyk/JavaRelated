package com.example.trading.domain;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record Portfolio(String traderId, BigDecimal cash, Map<String, Long> positions) {

    public Portfolio {
        traderId = DomainValidation.identifier(traderId, "traderId");
        cash = DomainValidation.money(cash, "cash", true);
        positions = normalizePositions(positions);
    }

    public long position(String symbol) {
        return positions.getOrDefault(DomainValidation.symbol(symbol, "symbol"), 0L);
    }

    public Portfolio buy(String symbol, long quantity, BigDecimal amount) {
        requireQuantityAndAmount(quantity, amount);
        if (cash.compareTo(amount) < 0) {
            throw new IllegalStateException("insufficient cash");
        }
        Map<String, Long> updated = new LinkedHashMap<>(positions);
        updated.merge(DomainValidation.symbol(symbol, "symbol"), quantity, Math::addExact);
        return new Portfolio(traderId, cash.subtract(amount), updated);
    }

    public Portfolio sell(String symbol, long quantity, BigDecimal amount) {
        requireQuantityAndAmount(quantity, amount);
        String normalized = DomainValidation.symbol(symbol, "symbol");
        long current = position(normalized);
        if (current < quantity) {
            throw new IllegalStateException("insufficient position");
        }
        Map<String, Long> updated = new LinkedHashMap<>(positions);
        long remaining = current - quantity;
        if (remaining == 0) {
            updated.remove(normalized);
        } else {
            updated.put(normalized, remaining);
        }
        return new Portfolio(traderId, cash.add(amount), updated);
    }

    private static Map<String, Long> normalizePositions(Map<String, Long> positions) {
        Map<String, Long> normalized = new LinkedHashMap<>();
        Objects.requireNonNull(positions, "positions").forEach((symbol, quantity) -> {
            String key = DomainValidation.symbol(symbol, "position symbol");
            long value = Objects.requireNonNull(quantity, "position quantity");
            if (value < 0 || value > DomainValidation.MAX_QUANTITY) {
                throw new IllegalArgumentException(
                        "position quantities must be between zero and "
                                + DomainValidation.MAX_QUANTITY);
            }
            if (value > 0) {
                normalized.merge(key, value, Math::addExact);
            }
        });
        return Map.copyOf(normalized);
    }

    private static void requireQuantityAndAmount(long quantity, BigDecimal amount) {
        DomainValidation.quantity(quantity, "quantity");
        DomainValidation.money(amount, "amount", false);
    }
}
