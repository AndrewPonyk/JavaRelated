package com.example.trading.infrastructure.concurrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.trading.application.exception.InsufficientFundsException;
import com.example.trading.application.exception.InsufficientPositionException;
import com.example.trading.application.exception.OrderStateException;
import com.example.trading.domain.Order;
import com.example.trading.domain.OrderSide;
import com.example.trading.domain.OrderStatus;
import com.example.trading.domain.Portfolio;
import com.example.trading.domain.Trade;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class ConcurrentPortfolioRepositoryTest {

    @Test
    void performsPortfolioCrudAndProtectsActiveReservations() {
        ConcurrentPortfolioRepository repository = new ConcurrentPortfolioRepository();
        Portfolio created = repository.create(
                new Portfolio("trader", new BigDecimal("1000"), Map.of("AAPL", 5L)));
        assertEquals(created, repository.findByTraderId("trader").orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> repository.create(created));

        Order order = order(1L, "trader", OrderSide.BUY, 1L, "100");
        repository.reserve(order);
        assertThrows(OrderStateException.class, () -> repository.update(created));
        assertThrows(OrderStateException.class, () -> repository.delete("trader"));
        repository.release(order);

        Portfolio updated =
                new Portfolio("trader", new BigDecimal("1100"), Map.of("AAPL", 4L));
        assertEquals(updated, repository.update(updated));
        assertEquals(updated, repository.delete("trader").orElseThrow());
        assertTrue(repository.delete("trader").isEmpty());
    }

    @Test
    void reservesAndSettlesBothSidesAtomically() {
        ConcurrentPortfolioRepository repository = new ConcurrentPortfolioRepository();
        repository.create(new Portfolio("buyer", new BigDecimal("1000"), Map.of()));
        repository.create(new Portfolio(
                "seller", new BigDecimal("100"), Map.of("AAPL", 10L)));
        Order buy = order(1L, "buyer", OrderSide.BUY, 4L, "225");
        Order sell = order(2L, "seller", OrderSide.SELL, 4L, "220");
        repository.reserve(buy);
        repository.reserve(sell);

        repository.settle(new Trade(
                1L, 1L, 2L, "buyer", "seller", "AAPL", 4L,
                new BigDecimal("220"), Instant.EPOCH));

        assertEquals(new BigDecimal("120"),
                repository.findByTraderId("buyer").orElseThrow().cash());
        assertEquals(4L, repository.findByTraderId("buyer").orElseThrow().position("AAPL"));
        assertEquals(new BigDecimal("980"),
                repository.findByTraderId("seller").orElseThrow().cash());
        assertEquals(6L, repository.findByTraderId("seller").orElseThrow().position("AAPL"));
        assertEquals(0, repository.activeReservationCount());

        ConcurrentPortfolioRepository failingRepository = new ConcurrentPortfolioRepository();
        failingRepository.create(new Portfolio("buyer", BigDecimal.ONE, Map.of()));
        Portfolio maximumCash = new Portfolio(
                "seller",
                new BigDecimal("999999999999999999999999"),
                Map.of("AAPL", 1L));
        failingRepository.create(maximumCash);
        Order failingBuy = order(3L, "buyer", OrderSide.BUY, 1L, "1");
        Order failingSell = order(4L, "seller", OrderSide.SELL, 1L, "1");
        failingRepository.reserve(failingBuy);
        failingRepository.reserve(failingSell);
        assertThrows(IllegalArgumentException.class, () -> failingRepository.settle(new Trade(
                2L, 3L, 4L, "buyer", "seller", "AAPL", 1L,
                BigDecimal.ONE, Instant.EPOCH)));
        assertEquals(BigDecimal.ONE,
                failingRepository.findByTraderId("buyer").orElseThrow().cash());
        assertEquals(maximumCash,
                failingRepository.findByTraderId("seller").orElseThrow());
        assertEquals(2, failingRepository.activeReservationCount());
    }

    @Test
    void rejectsOrdersThatExceedAvailableResources() {
        ConcurrentPortfolioRepository repository = new ConcurrentPortfolioRepository();
        repository.create(new Portfolio(
                "trader", new BigDecimal("100"), Map.of("AAPL", 2L)));
        repository.reserve(order(1L, "trader", OrderSide.BUY, 1L, "80"));
        assertThrows(InsufficientFundsException.class, () ->
                repository.reserve(order(2L, "trader", OrderSide.BUY, 1L, "30")));
        repository.reserve(order(3L, "trader", OrderSide.SELL, 2L, "100"));
        assertThrows(InsufficientPositionException.class, () ->
                repository.reserve(order(4L, "trader", OrderSide.SELL, 1L, "100")));
    }

    @Test
    void savesIndependentPortfoliosConcurrently() throws Exception {
        ConcurrentPortfolioRepository repository = new ConcurrentPortfolioRepository();
        int portfolioCount = 1_000;
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>(portfolioCount);
            for (int index = 0; index < portfolioCount; index++) {
                int traderNumber = index;
                futures.add(executor.submit(() -> repository.create(new Portfolio(
                        "trader-" + traderNumber,
                        new BigDecimal("10000.00"),
                        Map.of("AAPL", 10L)))));
            }
            for (Future<?> future : futures) {
                future.get();
            }
        }
        assertEquals(portfolioCount, repository.findAll().size());
    }

    @Test
    void snapshotsNeverExposeHalfOfATwoPortfolioSettlement() throws Exception {
        ConcurrentPortfolioRepository repository = new ConcurrentPortfolioRepository();
        repository.create(new Portfolio("buyer", new BigDecimal("10000"), Map.of()));
        repository.create(new Portfolio(
                "seller", BigDecimal.ZERO, Map.of("AAPL", 10_000L)));
        AtomicBoolean complete = new AtomicBoolean();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<?> writer = executor.submit(() -> {
                try {
                    for (long index = 1; index <= 10_000; index++) {
                        Order buy = order(index * 2L - 1L, "buyer", OrderSide.BUY, 1L, "1");
                        Order sell = order(index * 2L, "seller", OrderSide.SELL, 1L, "1");
                        repository.reserve(buy);
                        repository.reserve(sell);
                        repository.settle(new Trade(
                                index,
                                buy.id(),
                                sell.id(),
                                "buyer",
                                "seller",
                                "AAPL",
                                1L,
                                BigDecimal.ONE,
                                Instant.EPOCH));
                    }
                } finally {
                    complete.set(true);
                }
            });

            while (!complete.get()) {
                List<Portfolio> snapshot = repository.findAll();
                BigDecimal totalCash = snapshot.stream()
                        .map(Portfolio::cash)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                long totalShares = snapshot.stream()
                        .mapToLong(portfolio -> portfolio.position("AAPL"))
                        .sum();
                assertEquals(new BigDecimal("10000"), totalCash);
                assertEquals(10_000L, totalShares);
            }
            writer.get();
        }
    }

    private static Order order(
            long id,
            String traderId,
            OrderSide side,
            long quantity,
            String price) {
        return new Order(
                id, traderId, "AAPL", side, quantity, new BigDecimal(price),
                OrderStatus.OPEN, Instant.EPOCH);
    }
}
