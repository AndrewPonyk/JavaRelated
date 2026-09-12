package com.example.trading.application.port;

import com.example.trading.domain.Order;
import com.example.trading.domain.Portfolio;
import com.example.trading.domain.Trade;
import java.util.List;
import java.util.Optional;

public interface PortfolioRepository {
    Portfolio create(Portfolio portfolio);

    Portfolio update(Portfolio portfolio);

    Optional<Portfolio> delete(String traderId);

    Optional<Portfolio> findByTraderId(String traderId);

    List<Portfolio> findAll();

    void reserve(Order order);

    void release(Order order);

    void settle(Trade trade);

    int activeReservationCount();
}
