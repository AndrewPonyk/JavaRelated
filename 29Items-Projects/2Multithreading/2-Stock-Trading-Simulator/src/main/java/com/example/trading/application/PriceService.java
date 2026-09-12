package com.example.trading.application;

import com.example.trading.application.exception.PriceUnavailableException;
import com.example.trading.application.port.AuditLog;
import com.example.trading.application.port.PriceCache;
import com.example.trading.application.port.PriceProvider;
import com.example.trading.domain.AuditType;
import com.example.trading.domain.DomainValidation;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class PriceService {
    private final PriceCache cache;
    private final PriceProvider provider;
    private final Executor executor;
    private final Duration timeout;
    private final AuditLog auditLog;
    private final Clock clock;
    private final ConcurrentMap<String, CompletableFuture<BigDecimal>> inFlight =
            new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicLong> revisions = new ConcurrentHashMap<>();

    public PriceService(
            PriceCache cache,
            PriceProvider provider,
            Executor executor,
            Duration timeout,
            AuditLog auditLog,
            Clock clock) {
        this.cache = Objects.requireNonNull(cache, "cache");
        this.provider = Objects.requireNonNull(provider, "provider");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.timeout = requirePositive(timeout);
        this.auditLog = Objects.requireNonNull(auditLog, "auditLog");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public CompletableFuture<BigDecimal> getPrice(String symbol) {
        String normalized = normalizeSymbol(symbol);
        long requestRevision = revision(normalized).get();
        Optional<BigDecimal> cached = cache.get(normalized);
        if (cached.isPresent()) {
            return CompletableFuture.completedFuture(cached.orElseThrow());
        }

        CompletableFuture<BigDecimal> current = inFlight.get(normalized);
        if (current != null) {
            return current;
        }
        CompletableFuture<BigDecimal> published = new CompletableFuture<>();
        CompletableFuture<BigDecimal> winner = inFlight.putIfAbsent(normalized, published);
        if (winner != null) {
            return winner;
        }
        if (revision(normalized).get() != requestRevision) {
            inFlight.remove(normalized, published);
            Optional<BigDecimal> latest = cache.get(normalized);
            if (latest.isPresent()) {
                published.complete(latest.orElseThrow());
            } else {
                published.cancel(false);
            }
            return published;
        }
        CompletableFuture<BigDecimal> source = CompletableFuture
                .supplyAsync(() -> fetch(normalized), executor)
                .thenApply(PriceService::requireProviderPrice)
                .thenApply(price -> {
                    if (revision(normalized).get() == requestRevision) {
                        cache.put(normalized, price);
                    }
                    return price;
                })
                .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS);
        source.whenComplete((price, failure) -> {
            inFlight.remove(normalized, published);
            if (failure == null) {
                published.complete(price);
            } else {
                published.completeExceptionally(failure);
            }
        });
        return published;
    }

    public BigDecimal putPrice(String symbol, BigDecimal price) {
        String normalized = normalizeSymbol(symbol);
        BigDecimal valid = DomainValidation.money(price, "price", false);
        revision(normalized).incrementAndGet();
        cancelInFlight(normalized);
        cache.put(normalized, valid);
        auditLog.record(
                AuditType.PRICE_UPDATED,
                normalized,
                "price=" + valid.toPlainString(),
                Instant.now(clock));
        return valid;
    }

    public Optional<BigDecimal> deletePrice(String symbol) {
        String normalized = normalizeSymbol(symbol);
        revision(normalized).incrementAndGet();
        cancelInFlight(normalized);
        Optional<BigDecimal> removed = cache.remove(normalized);
        removed.ifPresent(price -> auditLog.record(
                AuditType.PRICE_DELETED,
                normalized,
                "price=" + price.toPlainString(),
                Instant.now(clock)));
        return removed;
    }

    public Map<String, BigDecimal> listCachedPrices() {
        return cache.snapshot();
    }

    int inFlightRequestCount() {
        return inFlight.size();
    }

    private BigDecimal fetch(String symbol) {
        try {
            return provider.fetch(symbol);
        } catch (RuntimeException exception) {
            throw new CompletionException(
                    new PriceUnavailableException("could not fetch price for " + symbol, exception));
        }
    }

    private static Duration requirePositive(Duration value) {
        Duration result = Objects.requireNonNull(value, "timeout");
        if (result.isZero() || result.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        return result;
    }

    private static BigDecimal requireProviderPrice(BigDecimal price) {
        try {
            return DomainValidation.money(price, "price", false);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("price provider returned an invalid price", exception);
        }
    }

    private static String normalizeSymbol(String symbol) {
        return DomainValidation.symbol(symbol, "symbol");
    }

    private AtomicLong revision(String symbol) {
        return revisions.computeIfAbsent(symbol, ignored -> new AtomicLong());
    }

    private void cancelInFlight(String symbol) {
        CompletableFuture<BigDecimal> request = inFlight.remove(symbol);
        if (request != null) {
            request.cancel(true);
        }
    }
}
