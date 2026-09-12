package com.example.trading.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DomainModelTest {

    @Test
    void orderTracksPartialAndCompleteFills() {
        Order order = order();
        Order partial = order.fill(4L, Instant.EPOCH.plusSeconds(1));
        assertEquals(OrderStatus.PARTIALLY_FILLED, partial.status());
        assertEquals(6L, partial.remainingQuantity());
        assertEquals(4L, partial.executedQuantity());
        assertTrue(partial.isActive());

        Order filled = partial.fill(6L, Instant.EPOCH.plusSeconds(2));
        assertEquals(OrderStatus.FILLED, filled.status());
        assertFalse(filled.isActive());
        assertThrows(IllegalStateException.class,
                () -> filled.cancel(Instant.EPOCH.plusSeconds(3)));
        assertThrows(IllegalArgumentException.class,
                () -> order.fill(11L, Instant.EPOCH.plusSeconds(1)));
    }

    @Test
    void portfolioDefensivelyCopiesAndAppliesTrades() {
        Map<String, Long> mutable = new HashMap<>();
        mutable.put("aapl", 5L);
        Portfolio portfolio = new Portfolio(" trader ", new BigDecimal("1000"), mutable);
        mutable.put("AAPL", 100L);
        assertEquals(5L, portfolio.position("AAPL"));

        Portfolio bought = portfolio.buy("AAPL", 2L, new BigDecimal("100"));
        assertEquals(new BigDecimal("900"), bought.cash());
        assertEquals(7L, bought.position("AAPL"));
        Portfolio sold = bought.sell("AAPL", 7L, new BigDecimal("350"));
        assertEquals(new BigDecimal("1250"), sold.cash());
        assertEquals(0L, sold.position("AAPL"));
        assertThrows(IllegalStateException.class,
                () -> portfolio.sell("AAPL", 6L, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class,
                () -> new Portfolio("x", new BigDecimal("-1"), Map.of()));
    }

    @Test
    void tradeCalculatesNotionalAndRejectsSelfTrades() {
        Trade trade = new Trade(
                1L, 2L, 3L, "buyer", "seller", "aapl", 4L,
                new BigDecimal("12.50"), Instant.EPOCH);
        assertEquals(new BigDecimal("50.00"), trade.notional());
        assertEquals("AAPL", trade.symbol());
        assertThrows(IllegalArgumentException.class, () -> new Trade(
                1L, 2L, 3L, "same", "same", "AAPL", 1L,
                BigDecimal.ONE, Instant.EPOCH));
    }

    @Test
    void rejectsMalformedAndUnboundedExternalValues() {
        assertThrows(IllegalArgumentException.class, () -> new Portfolio(
                "../trader", BigDecimal.ONE, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new Portfolio(
                "trader", new BigDecimal("1.00001"), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new Portfolio(
                "trader", new BigDecimal("1E+24"), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new Order(
                1L,
                "trader",
                "<script>",
                OrderSide.BUY,
                1L,
                BigDecimal.ONE,
                OrderStatus.OPEN,
                Instant.EPOCH));
        assertThrows(IllegalArgumentException.class, () -> new Order(
                1L,
                "trader",
                "AAPL",
                OrderSide.BUY,
                DomainValidation.MAX_QUANTITY + 1L,
                BigDecimal.ONE,
                OrderStatus.OPEN,
                Instant.EPOCH));
    }

    private static Order order() {
        return new Order(
                1L,
                "trader",
                "aapl",
                OrderSide.BUY,
                10L,
                new BigDecimal("100"),
                OrderStatus.OPEN,
                Instant.EPOCH);
    }
}
