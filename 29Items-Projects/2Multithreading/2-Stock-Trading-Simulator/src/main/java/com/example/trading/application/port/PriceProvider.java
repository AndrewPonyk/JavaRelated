package com.example.trading.application.port;

import java.math.BigDecimal;

@FunctionalInterface
public interface PriceProvider {
    BigDecimal fetch(String symbol);
}
