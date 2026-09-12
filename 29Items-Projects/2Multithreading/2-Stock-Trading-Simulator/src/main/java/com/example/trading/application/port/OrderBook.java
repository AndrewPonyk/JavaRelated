package com.example.trading.application.port;

import com.example.trading.domain.Order;
import com.example.trading.domain.OrderBookResult;
import java.util.List;
import java.util.Optional;

public interface OrderBook {
    OrderBookResult submit(Order order);

    Optional<Order> findById(long orderId);

    Optional<Order> cancel(long orderId);

    List<Order> snapshot();
}
