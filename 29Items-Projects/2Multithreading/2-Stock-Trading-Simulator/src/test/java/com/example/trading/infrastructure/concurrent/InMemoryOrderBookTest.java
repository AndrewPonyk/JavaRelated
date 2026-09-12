package com.example.trading.infrastructure.concurrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.trading.TestFixture;
import com.example.trading.domain.Order;
import com.example.trading.domain.OrderBookResult;
import com.example.trading.domain.OrderSide;
import com.example.trading.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class InMemoryOrderBookTest {

    @Test
    void matchesUsingPriceTimePriorityAndSupportsPartialFills() {
        InMemoryOrderBook orderBook = new InMemoryOrderBook(TestFixture.CLOCK, true);
        orderBook.submit(order(1L, "seller-1", OrderSide.SELL, 4L, "100.00", Instant.EPOCH));
        orderBook.submit(order(2L, "seller-2", OrderSide.SELL, 5L, "100.00", Instant.EPOCH.plusSeconds(1)));

        OrderBookResult result = orderBook.submit(
                order(3L, "buyer", OrderSide.BUY, 6L, "101.00", Instant.EPOCH.plusSeconds(2)));

        assertEquals(2, result.trades().size());
        assertEquals(4L, result.trades().getFirst().quantity());
        assertEquals(new BigDecimal("100.00"), result.trades().getFirst().executionPrice());
        assertEquals(OrderStatus.FILLED, result.order().status());
        assertEquals(OrderStatus.FILLED, orderBook.findById(1L).orElseThrow().status());
        Order secondSell = orderBook.findById(2L).orElseThrow();
        assertEquals(OrderStatus.PARTIALLY_FILLED, secondSell.status());
        assertEquals(3L, secondSell.remainingQuantity());
    }

    @Test
    void cancelsOpenOrdersAndPreventsSelfTrading() {
        InMemoryOrderBook orderBook = new InMemoryOrderBook(TestFixture.CLOCK, false);
        orderBook.submit(order(1L, "same", OrderSide.SELL, 5L, "100.00", Instant.EPOCH));
        OrderBookResult selfTrade = orderBook.submit(
                order(2L, "same", OrderSide.BUY, 5L, "100.00", Instant.EPOCH.plusSeconds(1)));

        assertEquals(OrderStatus.CANCELLED, selfTrade.order().status());
        assertTrue(selfTrade.trades().isEmpty());
        assertEquals(OrderStatus.CANCELLED, orderBook.cancel(1L).orElseThrow().status());
        assertTrue(orderBook.cancel(1L).isEmpty());
    }

    @Test
    void serializesConcurrentWritesAndRejectsDuplicateIds() throws Exception {
        InMemoryOrderBook orderBook = new InMemoryOrderBook(TestFixture.CLOCK, true);
        int orderCount = 1_000;
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<OrderBookResult>> futures = new ArrayList<>(orderCount);
            for (int index = 0; index < orderCount; index++) {
                long id = index + 1L;
                futures.add(executor.submit(() -> orderBook.submit(
                        order(id, "trader-" + id, OrderSide.BUY, 1L, "99.00", Instant.EPOCH))));
            }
            for (Future<OrderBookResult> future : futures) {
                future.get();
            }
        }

        assertEquals(orderCount, orderBook.snapshot().size());
        assertThrows(IllegalArgumentException.class, () -> orderBook.submit(
                order(1L, "duplicate", OrderSide.BUY, 1L, "99.00", Instant.EPOCH)));
        assertThrows(UnsupportedOperationException.class, () -> orderBook.snapshot().clear());
    }

    private static Order order(
            long id,
            String traderId,
            OrderSide side,
            long quantity,
            String price,
            Instant createdAt) {
        return new Order(
                id,
                traderId,
                "AAPL",
                side,
                quantity,
                new BigDecimal(price),
                OrderStatus.OPEN,
                createdAt);
    }
}
