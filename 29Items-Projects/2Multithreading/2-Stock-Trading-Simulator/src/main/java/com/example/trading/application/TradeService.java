package com.example.trading.application;

import com.example.trading.application.port.TradeRepository;
import com.example.trading.domain.Trade;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class TradeService {
    private final TradeRepository repository;

    public TradeService(TradeRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public Optional<Trade> find(long tradeId) {
        if (tradeId <= 0) {
            throw new IllegalArgumentException("tradeId must be positive");
        }
        return repository.findById(tradeId);
    }

    public List<Trade> list() {
        return repository.findAll();
    }
}
