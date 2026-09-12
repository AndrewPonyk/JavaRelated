package com.example.trading.domain;

import java.util.List;
import java.util.Objects;

public record OrderBookResult(Order order, List<Trade> trades, List<Order> affectedOrders) {
    public OrderBookResult {
        order = Objects.requireNonNull(order, "order");
        trades = List.copyOf(Objects.requireNonNull(trades, "trades"));
        affectedOrders = List.copyOf(Objects.requireNonNull(affectedOrders, "affectedOrders"));
        if (!affectedOrders.contains(order)) {
            throw new IllegalArgumentException("affectedOrders must include the submitted order");
        }
    }
}
