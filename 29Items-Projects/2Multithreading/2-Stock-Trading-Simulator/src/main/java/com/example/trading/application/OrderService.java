package com.example.trading.application;

import com.example.trading.application.port.AuditLog;
import com.example.trading.application.port.OrderBook;
import com.example.trading.application.port.OrderIdGenerator;
import com.example.trading.application.port.PortfolioRepository;
import com.example.trading.application.port.TradeRepository;
import com.example.trading.domain.AuditType;
import com.example.trading.domain.Order;
import com.example.trading.domain.OrderBookResult;
import com.example.trading.domain.OrderStatus;
import com.example.trading.domain.Trade;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class OrderService {
    private final OrderBook orderBook;
    private final OrderIdGenerator idGenerator;
    private final PortfolioRepository portfolios;
    private final TradeRepository trades;
    private final AuditLog auditLog;
    private final Clock clock;

    public OrderService(
            OrderBook orderBook,
            OrderIdGenerator idGenerator,
            PortfolioRepository portfolios,
            TradeRepository trades,
            AuditLog auditLog,
            Clock clock) {
        this.orderBook = Objects.requireNonNull(orderBook, "orderBook");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
        this.portfolios = Objects.requireNonNull(portfolios, "portfolios");
        this.trades = Objects.requireNonNull(trades, "trades");
        this.auditLog = Objects.requireNonNull(auditLog, "auditLog");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Order place(PlaceOrderCommand command) {
        Objects.requireNonNull(command, "command");
        Instant createdAt = Instant.now(clock);
        Order order = new Order(
                idGenerator.nextId(),
                command.traderId(),
                command.symbol(),
                command.side(),
                command.quantity(),
                command.limitPrice(),
                OrderStatus.OPEN,
                createdAt);
        portfolios.reserve(order);
        OrderBookResult result;
        try {
            result = orderBook.submit(order);
        } catch (RuntimeException exception) {
            portfolios.release(order);
            throw exception;
        }

        for (Trade trade : result.trades()) {
            portfolios.settle(trade);
        }
        trades.saveAll(result.trades());
        if (result.order().status() == OrderStatus.CANCELLED) {
            portfolios.release(result.order());
        }
        recordOrderOutcome(result);
        return result.order();
    }

    public Optional<Order> find(long orderId) {
        requirePositiveId(orderId);
        return orderBook.findById(orderId);
    }

    public Optional<Order> cancel(long orderId) {
        requirePositiveId(orderId);
        Optional<Order> result = orderBook.cancel(orderId);
        result.ifPresent(order -> {
            portfolios.release(order);
            auditLog.record(
                    AuditType.ORDER_CANCELLED,
                    Long.toString(order.id()),
                    "remainingQuantity=" + order.remainingQuantity(),
                    Instant.now(clock));
        });
        return result;
    }

    public List<Order> list() {
        return orderBook.snapshot();
    }

    private void recordOrderOutcome(OrderBookResult result) {
        Order order = result.order();
        auditLog.record(
                AuditType.ORDER_PLACED,
                Long.toString(order.id()),
                "status=" + order.status() + ",quantity=" + order.quantity(),
                Instant.now(clock));
        for (Trade trade : result.trades()) {
            auditLog.record(
                    AuditType.TRADE_EXECUTED,
                    Long.toString(trade.id()),
                    "buyOrder=" + trade.buyOrderId()
                            + ",sellOrder=" + trade.sellOrderId()
                            + ",quantity=" + trade.quantity(),
                    trade.executedAt());
        }
        for (Order affected : result.affectedOrders()) {
            recordOrderStatus(affected);
        }
    }

    private void recordOrderStatus(Order order) {
        AuditType type;
        String details;
        if (order.status() == OrderStatus.FILLED) {
            type = AuditType.ORDER_FILLED;
            details = "executedQuantity=" + order.executedQuantity();
        } else if (order.status() == OrderStatus.PARTIALLY_FILLED) {
            type = AuditType.ORDER_PARTIALLY_FILLED;
            details = "remainingQuantity=" + order.remainingQuantity();
        } else if (order.status() == OrderStatus.CANCELLED) {
            type = AuditType.ORDER_CANCELLED;
            details = "remainingQuantity=" + order.remainingQuantity();
        } else {
            return;
        }
        auditLog.record(
                type,
                Long.toString(order.id()),
                details,
                Instant.now(clock));
    }

    private static void requirePositiveId(long orderId) {
        if (orderId <= 0) {
            throw new IllegalArgumentException("orderId must be positive");
        }
    }
}
