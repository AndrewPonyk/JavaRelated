package com.example.trading.application.port;

import com.example.trading.domain.Trade;
import java.util.List;
import java.util.Optional;

public interface TradeRepository {
    void saveAll(List<Trade> trades);

    Optional<Trade> findById(long tradeId);

    List<Trade> findAll();
}
