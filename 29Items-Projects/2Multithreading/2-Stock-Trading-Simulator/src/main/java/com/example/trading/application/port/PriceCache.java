package com.example.trading.application.port;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

public interface PriceCache {
    Optional<BigDecimal> get(String symbol);

    void put(String symbol, BigDecimal price);

    Optional<BigDecimal> remove(String symbol);

    Map<String, BigDecimal> snapshot();
}
