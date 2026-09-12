package com.example.trading.infrastructure.concurrent;

import com.example.trading.application.port.PriceCache;
import com.example.trading.domain.DomainValidation;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.StampedLock;

public final class StampedPriceCache implements PriceCache {
    private final StampedLock lock = new StampedLock();
    private final Map<String, BigDecimal> prices = new HashMap<>();
    private final AtomicLong optimisticReads = new AtomicLong();
    private final AtomicLong fallbackReads = new AtomicLong();

    @Override
    public Optional<BigDecimal> get(String symbol) {
        String key = normalizeSymbol(symbol);
        long stamp = lock.tryOptimisticRead();
        BigDecimal price = prices.get(key);
        if (!lock.validate(stamp)) {
            fallbackReads.incrementAndGet();
            stamp = lock.readLock();
            try {
                price = prices.get(key);
            } finally {
                lock.unlockRead(stamp);
            }
        } else {
            optimisticReads.incrementAndGet();
        }
        return Optional.ofNullable(price);
    }

    @Override
    public void put(String symbol, BigDecimal price) {
        String key = normalizeSymbol(symbol);
        BigDecimal validPrice = DomainValidation.money(price, "price", false);
        long stamp = lock.writeLock();
        try {
            prices.put(key, validPrice);
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    @Override
    public Optional<BigDecimal> remove(String symbol) {
        String key = normalizeSymbol(symbol);
        long stamp = lock.writeLock();
        try {
            return Optional.ofNullable(prices.remove(key));
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    @Override
    public Map<String, BigDecimal> snapshot() {
        long stamp = lock.readLock();
        try {
            return Map.copyOf(prices);
        } finally {
            lock.unlockRead(stamp);
        }
    }

    public long optimisticReadCount() {
        return optimisticReads.get();
    }

    public long fallbackReadCount() {
        return fallbackReads.get();
    }

    private static String normalizeSymbol(String symbol) {
        return DomainValidation.symbol(symbol, "symbol");
    }
}
