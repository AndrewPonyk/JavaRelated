package com.example.trading.infrastructure.concurrent;

import com.example.trading.application.port.OrderBook;
import com.example.trading.domain.Order;
import com.example.trading.domain.OrderBookResult;
import com.example.trading.domain.OrderSide;
import com.example.trading.domain.Trade;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

public final class InMemoryOrderBook implements OrderBook {
    private final ReentrantLock writeLock;
    private final Map<Long, Order> orders = new HashMap<>();
    private final Map<String, SymbolBook> books = new HashMap<>();
    private final AtomicLong tradeIds = new AtomicLong(1L);
    private final Clock clock;

    public InMemoryOrderBook() {
        this(Clock.systemUTC(), true);
    }

    public InMemoryOrderBook(Clock clock, boolean fairLock) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.writeLock = new ReentrantLock(fairLock);
    }

    @Override
    public OrderBookResult submit(Order order) {
        Objects.requireNonNull(order, "order");
        if (!order.isActive() || order.executedQuantity() != 0) {
            throw new IllegalArgumentException("only new open orders can be submitted");
        }
        writeLock.lock();
        try {
            if (orders.putIfAbsent(order.id(), order) != null) {
                throw new IllegalArgumentException("duplicate order ID: " + order.id());
            }
            SymbolBook book = books.computeIfAbsent(order.symbol(), ignored -> new SymbolBook());
            book.queue(order.side()).add(order.id());
            List<Trade> trades = match(book, order.symbol(), order.id());
            return new OrderBookResult(
                    orders.get(order.id()),
                    trades,
                    affectedOrders(order.id(), trades));
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public Optional<Order> findById(long orderId) {
        requirePositiveId(orderId);
        writeLock.lock();
        try {
            return Optional.ofNullable(orders.get(orderId));
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public Optional<Order> cancel(long orderId) {
        requirePositiveId(orderId);
        writeLock.lock();
        try {
            Order current = orders.get(orderId);
            if (current == null || !current.isActive()) {
                return Optional.empty();
            }
            Order cancelled = current.cancel(Instant.now(clock));
            orders.put(orderId, cancelled);
            SymbolBook book = books.get(current.symbol());
            if (book != null) {
                book.queue(current.side()).remove(orderId);
            }
            return Optional.of(cancelled);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public List<Order> snapshot() {
        writeLock.lock();
        try {
            return orders.values().stream()
                    .sorted(Comparator.comparingLong(Order::id))
                    .toList();
        } finally {
            writeLock.unlock();
        }
    }

    private List<Trade> match(SymbolBook book, String symbol, long submittedOrderId) {
        List<Trade> trades = new ArrayList<>();
        while (!book.buys.isEmpty() && !book.sells.isEmpty()) {
            Order buy = orders.get(book.buys.peek());
            Order sell = orders.get(book.sells.peek());
            if (buy.limitPrice().compareTo(sell.limitPrice()) < 0) {
                break;
            }
            if (buy.traderId().equals(sell.traderId())) {
                long cancelledId = buy.id() == submittedOrderId ? buy.id() : sell.id();
                Order cancelled = orders.get(cancelledId).cancel(Instant.now(clock));
                orders.put(cancelledId, cancelled);
                book.queue(cancelled.side()).remove(cancelledId);
                continue;
            }

            long quantity = Math.min(buy.remainingQuantity(), sell.remainingQuantity());
            Instant executedAt = Instant.now(clock);
            BigDecimal price = isOlder(buy, sell) ? buy.limitPrice() : sell.limitPrice();
            Trade trade = new Trade(
                    nextTradeId(),
                    buy.id(),
                    sell.id(),
                    buy.traderId(),
                    sell.traderId(),
                    symbol,
                    quantity,
                    price,
                    executedAt);
            trades.add(trade);
            updateFilledOrder(book.buys, buy.fill(quantity, executedAt));
            updateFilledOrder(book.sells, sell.fill(quantity, executedAt));
        }
        return List.copyOf(trades);
    }

    private void updateFilledOrder(PriorityQueue<Long> queue, Order updated) {
        orders.put(updated.id(), updated);
        if (!updated.isActive()) {
            queue.remove(updated.id());
        }
    }

    private List<Order> affectedOrders(long submittedOrderId, List<Trade> trades) {
        LinkedHashSet<Long> affectedIds = new LinkedHashSet<>();
        affectedIds.add(submittedOrderId);
        for (Trade trade : trades) {
            affectedIds.add(trade.buyOrderId());
            affectedIds.add(trade.sellOrderId());
        }
        return affectedIds.stream().map(orders::get).toList();
    }

    private long nextTradeId() {
        long id = tradeIds.getAndIncrement();
        if (id <= 0) {
            throw new IllegalStateException("trade ID sequence exhausted");
        }
        return id;
    }

    private static void requirePositiveId(long orderId) {
        if (orderId <= 0) {
            throw new IllegalArgumentException("orderId must be positive");
        }
    }

    private static boolean isOlder(Order left, Order right) {
        int timeComparison = left.createdAt().compareTo(right.createdAt());
        return timeComparison < 0 || (timeComparison == 0 && left.id() < right.id());
    }

    private final class SymbolBook {
        private final PriorityQueue<Long> buys = new PriorityQueue<>((leftId, rightId) -> {
            Order left = orders.get(leftId);
            Order right = orders.get(rightId);
            int price = right.limitPrice().compareTo(left.limitPrice());
            return price != 0 ? price : compareTime(left, right);
        });
        private final PriorityQueue<Long> sells = new PriorityQueue<>((leftId, rightId) -> {
            Order left = orders.get(leftId);
            Order right = orders.get(rightId);
            int price = left.limitPrice().compareTo(right.limitPrice());
            return price != 0 ? price : compareTime(left, right);
        });

        private PriorityQueue<Long> queue(OrderSide side) {
            return side == OrderSide.BUY ? buys : sells;
        }

        private int compareTime(Order left, Order right) {
            int result = left.createdAt().compareTo(right.createdAt());
            return result != 0 ? result : Long.compare(left.id(), right.id());
        }
    }
}
