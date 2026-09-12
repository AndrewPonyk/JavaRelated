package com.example.trading.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.trading.TestFixture;
import com.example.trading.domain.AuditType;
import com.example.trading.domain.Order;
import com.example.trading.domain.OrderSide;
import com.example.trading.domain.OrderStatus;
import com.example.trading.domain.Portfolio;
import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

class TradingSimulatorTest {

    @Test
    void executesEndToEndTradeAndConservesCashAndShares() {
        try (TestFixture.Context context = TestFixture.context()) {
            TradingSimulator simulator = context.simulator();
            simulator.createPortfolio(new Portfolio(
                    "buyer", new BigDecimal("1000.00"), Map.of()));
            simulator.createPortfolio(new Portfolio(
                    "seller", new BigDecimal("100.00"), Map.of("AAPL", 10L)));

            Order sell = simulator.placeOrder(command(
                    "seller", OrderSide.SELL, 6L, "220.00")).join();
            Order buy = simulator.placeOrder(command(
                    "buyer", OrderSide.BUY, 4L, "225.00")).join();

            assertEquals(OrderStatus.PARTIALLY_FILLED,
                    simulator.getOrder(sell.id()).orElseThrow().status());
            assertEquals(OrderStatus.FILLED, buy.status());
            assertEquals(1, simulator.listTrades().size());
            assertEquals(new BigDecimal("120.00"),
                    simulator.getPortfolio("buyer").orElseThrow().cash());
            assertEquals(4L,
                    simulator.getPortfolio("buyer").orElseThrow().position("AAPL"));
            assertEquals(new BigDecimal("980.00"),
                    simulator.getPortfolio("seller").orElseThrow().cash());
            assertEquals(6L,
                    simulator.getPortfolio("seller").orElseThrow().position("AAPL"));
            assertEquals(1, context.portfolios().activeReservationCount());
            assertTrue(simulator.listAuditEvents().stream().anyMatch(event ->
                    event.type() == AuditType.ORDER_PARTIALLY_FILLED
                            && event.entityId().equals(Long.toString(sell.id()))));

            assertEquals(OrderStatus.CANCELLED,
                    simulator.cancelOrder(sell.id()).orElseThrow().status());
            assertEquals(0, context.portfolios().activeReservationCount());
            assertTrue(simulator.listAuditEvents().size() >= 6);

            Order selfSell = simulator.placeOrder(command(
                    "seller", OrderSide.SELL, 1L, "220.00")).join();
            Order selfBuy = simulator.placeOrder(command(
                    "seller", OrderSide.BUY, 1L, "225.00")).join();
            assertEquals(OrderStatus.CANCELLED, selfBuy.status());
            assertTrue(simulator.listAuditEvents().stream().anyMatch(event ->
                    event.type() == AuditType.ORDER_CANCELLED
                            && event.entityId().equals(Long.toString(selfBuy.id()))));
            assertEquals(1, context.portfolios().activeReservationCount());
            simulator.cancelOrder(selfSell.id());
        }
    }

    @Test
    void providesPortfolioPriceOrderAndTradeFacadeOperations() {
        try (TestFixture.Context context = TestFixture.context()) {
            TradingSimulator simulator = context.simulator();
            Portfolio created = simulator.createPortfolio(
                    new Portfolio("trader", new BigDecimal("1000"), Map.of()));
            assertEquals(created, simulator.getPortfolio("trader").orElseThrow());

            Portfolio updated = simulator.updatePortfolio(
                    new Portfolio("trader", new BigDecimal("1200"), Map.of("MSFT", 2L)));
            assertEquals(updated, simulator.listPortfolios().getFirst());
            assertEquals(1, simulator.listPortfolios(0, 1).items().size());
            assertTrue(!simulator.listPortfolios(0, 1).hasNext());
            assertThrows(IllegalArgumentException.class,
                    () -> simulator.listPortfolios(0, Page.MAX_PAGE_SIZE + 1));
            assertEquals(new BigDecimal("10"), simulator.updatePrice("TEST", new BigDecimal("10")));
            assertEquals(new BigDecimal("10"), simulator.getPrice("TEST").join());
            assertTrue(simulator.listPrices().containsKey("TEST"));
            assertEquals(new BigDecimal("10"), simulator.deletePrice("TEST").orElseThrow());
            assertTrue(simulator.deletePrice("TEST").isEmpty());
            assertEquals(updated, simulator.deletePortfolio("trader"));
            assertTrue(simulator.getPortfolio("trader").isEmpty());
        }
    }

    @Test
    void validatesPriceBandsAndReplacesActiveOrders() {
        try (TestFixture.Context context = TestFixture.context()) {
            TradingSimulator simulator = context.simulator();
            simulator.createPortfolio(
                    new Portfolio("buyer", new BigDecimal("10000"), Map.of()));
            Order original = simulator.placeOrder(
                    command("buyer", OrderSide.BUY, 1L, "200.00")).join();

            Order replacement = simulator.replaceOrder(
                    original.id(),
                    command("buyer", OrderSide.BUY, 2L, "210.00")).join();
            assertEquals(OrderStatus.CANCELLED,
                    simulator.getOrder(original.id()).orElseThrow().status());
            assertTrue(replacement.id() > original.id());
            CompletionException error = assertThrows(CompletionException.class, () ->
                    simulator.placeOrder(command("buyer", OrderSide.BUY, 1L, "100.00")).join());
            assertTrue(error.getCause() instanceof IllegalArgumentException);
        }
    }

    private static PlaceOrderCommand command(
            String trader,
            OrderSide side,
            long quantity,
            String price) {
        return new PlaceOrderCommand(
                trader, "AAPL", side, quantity, new BigDecimal(price));
    }
}
