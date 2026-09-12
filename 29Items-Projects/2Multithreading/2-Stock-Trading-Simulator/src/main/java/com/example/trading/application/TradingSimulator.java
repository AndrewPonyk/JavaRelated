package com.example.trading.application;

import com.example.trading.application.exception.OrderStateException;
import com.example.trading.application.port.AuditLog;
import com.example.trading.domain.AuditEvent;
import com.example.trading.domain.DomainValidation;
import com.example.trading.domain.Order;
import com.example.trading.domain.Portfolio;
import com.example.trading.domain.Trade;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class TradingSimulator {
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final OrderService orders;
    private final PortfolioService portfolios;
    private final TradeService trades;
    private final PriceService prices;
    private final ProfitCalculationService profits;
    private final AuditLog auditLog;
    private final BigDecimal maximumPriceDeviationPercent;

    public TradingSimulator(
            OrderService orders,
            PortfolioService portfolios,
            TradeService trades,
            PriceService prices,
            ProfitCalculationService profits,
            AuditLog auditLog,
            BigDecimal maximumPriceDeviationPercent) {
        this.orders = Objects.requireNonNull(orders, "orders");
        this.portfolios = Objects.requireNonNull(portfolios, "portfolios");
        this.trades = Objects.requireNonNull(trades, "trades");
        this.prices = Objects.requireNonNull(prices, "prices");
        this.profits = Objects.requireNonNull(profits, "profits");
        this.auditLog = Objects.requireNonNull(auditLog, "auditLog");
        this.maximumPriceDeviationPercent = requirePercentage(maximumPriceDeviationPercent);
    }

    public CompletableFuture<Order> placeOrder(PlaceOrderCommand command) {
        Objects.requireNonNull(command, "command");
        return prices.getPrice(command.symbol())
                .thenApply(referencePrice -> validateLimit(command, referencePrice))
                .thenApply(orders::place);
    }

    public CompletableFuture<Order> replaceOrder(long orderId, PlaceOrderCommand replacement) {
        Objects.requireNonNull(replacement, "replacement");
        Order current = orders.find(orderId)
                .orElseThrow(() -> new OrderStateException("order does not exist: " + orderId));
        if (!current.isActive()) {
            throw new OrderStateException("only active orders can be replaced: " + orderId);
        }
        if (!current.traderId().equals(replacement.traderId())) {
            throw new OrderStateException("replacement trader must own the original order");
        }
        return prices.getPrice(replacement.symbol())
                .thenApply(referencePrice -> validateLimit(replacement, referencePrice))
                .thenApply(validated -> {
                    if (orders.cancel(orderId).isEmpty()) {
                        throw new OrderStateException(
                                "order changed concurrently and could not be replaced: " + orderId);
                    }
                    return orders.place(validated);
                });
    }

    public Optional<Order> getOrder(long orderId) {
        return orders.find(orderId);
    }

    public List<Order> listOrders() {
        return orders.list();
    }

    public Page<Order> listOrders(int offset, int limit) {
        return Page.from(orders.list(), offset, limit);
    }

    public Optional<Order> cancelOrder(long orderId) {
        return orders.cancel(orderId);
    }

    public Portfolio createPortfolio(Portfolio portfolio) {
        return portfolios.create(portfolio);
    }

    public Portfolio updatePortfolio(Portfolio portfolio) {
        return portfolios.update(portfolio);
    }

    public Optional<Portfolio> getPortfolio(String traderId) {
        return portfolios.find(traderId);
    }

    public List<Portfolio> listPortfolios() {
        return portfolios.list();
    }

    public Page<Portfolio> listPortfolios(int offset, int limit) {
        return Page.from(portfolios.list(), offset, limit);
    }

    public Portfolio deletePortfolio(String traderId) {
        return portfolios.delete(traderId);
    }

    public CompletableFuture<BigDecimal> getPrice(String symbol) {
        return prices.getPrice(symbol);
    }

    public BigDecimal updatePrice(String symbol, BigDecimal price) {
        return prices.putPrice(symbol, price);
    }

    public Optional<BigDecimal> deletePrice(String symbol) {
        return prices.deletePrice(symbol);
    }

    public Map<String, BigDecimal> listPrices() {
        return prices.listCachedPrices();
    }

    public Optional<Trade> getTrade(long tradeId) {
        return trades.find(tradeId);
    }

    public List<Trade> listTrades() {
        return trades.list();
    }

    public Page<Trade> listTrades(int offset, int limit) {
        return Page.from(trades.list(), offset, limit);
    }

    public List<AuditEvent> listAuditEvents() {
        return auditLog.findAll();
    }

    public Page<AuditEvent> listAuditEvents(int offset, int limit) {
        return Page.from(auditLog.findAll(), offset, limit);
    }

    public BigDecimal calculateAggregateProfit(
            Map<String, BigDecimal> marketPrices,
            BigDecimal initialCapitalPerPortfolio) {
        return profits.calculateAggregateProfit(marketPrices, initialCapitalPerPortfolio);
    }

    private PlaceOrderCommand validateLimit(
            PlaceOrderCommand command,
            BigDecimal referencePrice) {
        BigDecimal deviationPercent = command.limitPrice()
                .subtract(referencePrice)
                .abs()
                .multiply(ONE_HUNDRED)
                .divide(referencePrice, MathContext.DECIMAL64);
        if (deviationPercent.compareTo(maximumPriceDeviationPercent) > 0) {
            throw new IllegalArgumentException(
                    "limit price deviates from the reference price by "
                            + deviationPercent.stripTrailingZeros().toPlainString()
                            + "%; maximum is "
                            + maximumPriceDeviationPercent.stripTrailingZeros().toPlainString()
                            + "%");
        }
        return command;
    }

    private static BigDecimal requirePercentage(BigDecimal percentage) {
        BigDecimal value = DomainValidation.money(
                percentage, "maximumPriceDeviationPercent", true);
        if (value.signum() < 0 || value.compareTo(ONE_HUNDRED) > 0) {
            throw new IllegalArgumentException(
                    "maximumPriceDeviationPercent must be between 0 and 100");
        }
        return value;
    }
}
