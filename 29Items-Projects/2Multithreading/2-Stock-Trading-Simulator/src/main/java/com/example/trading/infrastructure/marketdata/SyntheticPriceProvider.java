package com.example.trading.infrastructure.marketdata;

import com.example.trading.application.port.PriceProvider;
import com.example.trading.domain.DomainValidation;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class SyntheticPriceProvider implements PriceProvider {
    private final ConcurrentMap<String, BigDecimal> prices;

    public SyntheticPriceProvider() {
        this(Map.of(
                "AAPL", new BigDecimal("225.00"),
                "MSFT", new BigDecimal("415.00"),
                "NVDA", new BigDecimal("135.00")));
    }

    public SyntheticPriceProvider(Map<String, BigDecimal> initialPrices) {
        prices = new ConcurrentHashMap<>();
        Objects.requireNonNull(initialPrices, "initialPrices")
                .forEach(this::put);
    }

    @Override
    public BigDecimal fetch(String symbol) {
        String key = normalize(symbol);
        BigDecimal price = prices.get(key);
        if (price == null) {
            throw new IllegalArgumentException("no synthetic price for " + key);
        }
        return price;
    }

    public BigDecimal put(String symbol, BigDecimal price) {
        String key = normalize(symbol);
        BigDecimal value = DomainValidation.money(price, "price", false);
        prices.put(key, value);
        return value;
    }

    public Optional<BigDecimal> remove(String symbol) {
        return Optional.ofNullable(prices.remove(normalize(symbol)));
    }

    public Map<String, BigDecimal> snapshot() {
        return Map.copyOf(prices);
    }

    private static String normalize(String symbol) {
        return DomainValidation.symbol(symbol, "symbol");
    }
}
