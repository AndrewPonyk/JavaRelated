package com.example.trading.infrastructure.concurrent;

import com.example.trading.application.port.TradeRepository;
import com.example.trading.domain.Trade;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

public final class ConcurrentTradeRepository implements TradeRepository {
    private final ConcurrentMap<Long, Trade> trades = new ConcurrentHashMap<>();
    private final ReentrantLock appendLock = new ReentrantLock();

    @Override
    public void saveAll(List<Trade> values) {
        List<Trade> batch = List.copyOf(values);
        appendLock.lock();
        try {
            Set<Long> batchIds = new HashSet<>();
            for (Trade trade : batch) {
                if (!batchIds.add(trade.id()) || trades.containsKey(trade.id())) {
                    throw new IllegalArgumentException("duplicate trade ID: " + trade.id());
                }
            }
            batch.forEach(trade -> trades.put(trade.id(), trade));
        } finally {
            appendLock.unlock();
        }
    }

    @Override
    public Optional<Trade> findById(long tradeId) {
        if (tradeId <= 0) {
            throw new IllegalArgumentException("tradeId must be positive");
        }
        return Optional.ofNullable(trades.get(tradeId));
    }

    @Override
    public List<Trade> findAll() {
        return trades.values().stream()
                .sorted(Comparator.comparingLong(Trade::id))
                .toList();
    }
}
