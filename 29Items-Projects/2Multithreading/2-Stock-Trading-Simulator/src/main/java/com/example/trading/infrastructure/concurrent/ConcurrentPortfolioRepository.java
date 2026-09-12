package com.example.trading.infrastructure.concurrent;

import com.example.trading.application.exception.InsufficientFundsException;
import com.example.trading.application.exception.InsufficientPositionException;
import com.example.trading.application.exception.OrderStateException;
import com.example.trading.application.port.PortfolioRepository;
import com.example.trading.domain.Order;
import com.example.trading.domain.OrderSide;
import com.example.trading.domain.Portfolio;
import com.example.trading.domain.Trade;
import com.example.trading.domain.DomainValidation;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

public final class ConcurrentPortfolioRepository implements PortfolioRepository {
    private final ConcurrentMap<String, Portfolio> portfolios = new ConcurrentHashMap<>();
    private final Map<Long, Reservation> reservations = new HashMap<>();
    private final ReentrantLock transactionLock = new ReentrantLock(true);

    @Override
    public Portfolio create(Portfolio portfolio) {
        Portfolio value = Objects.requireNonNull(portfolio, "portfolio");
        transactionLock.lock();
        try {
            if (portfolios.putIfAbsent(value.traderId(), value) != null) {
                throw new IllegalArgumentException("portfolio already exists: " + value.traderId());
            }
            return value;
        } finally {
            transactionLock.unlock();
        }
    }

    @Override
    public Portfolio update(Portfolio portfolio) {
        Portfolio value = Objects.requireNonNull(portfolio, "portfolio");
        transactionLock.lock();
        try {
            requireNoReservations(value.traderId());
            if (portfolios.replace(value.traderId(), value) == null) {
                throw new IllegalArgumentException("portfolio does not exist: " + value.traderId());
            }
            return value;
        } finally {
            transactionLock.unlock();
        }
    }

    @Override
    public Optional<Portfolio> delete(String traderId) {
        String key = normalizeTraderId(traderId);
        transactionLock.lock();
        try {
            requireNoReservations(key);
            return Optional.ofNullable(portfolios.remove(key));
        } finally {
            transactionLock.unlock();
        }
    }

    @Override
    public Optional<Portfolio> findByTraderId(String traderId) {
        String key = DomainValidation.identifier(traderId, "traderId");
        transactionLock.lock();
        try {
            return Optional.ofNullable(portfolios.get(key));
        } finally {
            transactionLock.unlock();
        }
    }

    @Override
    public List<Portfolio> findAll() {
        transactionLock.lock();
        try {
            return portfolios.values().stream()
                    .sorted(Comparator.comparing(Portfolio::traderId))
                    .toList();
        } finally {
            transactionLock.unlock();
        }
    }

    @Override
    public void reserve(Order order) {
        Objects.requireNonNull(order, "order");
        transactionLock.lock();
        try {
            Portfolio portfolio = requirePortfolio(order.traderId());
            if (reservations.containsKey(order.id())) {
                throw new OrderStateException("order already has a reservation: " + order.id());
            }
            if (order.side() == OrderSide.BUY) {
                BigDecimal required = order.limitPrice()
                        .multiply(BigDecimal.valueOf(order.remainingQuantity()));
                DomainValidation.money(required, "order notional", false);
                BigDecimal available = portfolio.cash().subtract(reservedCash(order.traderId()));
                if (available.compareTo(required) < 0) {
                    throw new InsufficientFundsException(
                            "insufficient available cash for trader " + order.traderId());
                }
            } else {
                long available = portfolio.position(order.symbol())
                        - reservedPosition(order.traderId(), order.symbol());
                if (available < order.remainingQuantity()) {
                    throw new InsufficientPositionException(
                            "insufficient available " + order.symbol() + " for trader " + order.traderId());
                }
            }
            reservations.put(order.id(), Reservation.from(order));
        } finally {
            transactionLock.unlock();
        }
    }

    @Override
    public void release(Order order) {
        Objects.requireNonNull(order, "order");
        transactionLock.lock();
        try {
            reservations.remove(order.id());
        } finally {
            transactionLock.unlock();
        }
    }

    @Override
    public void settle(Trade trade) {
        Objects.requireNonNull(trade, "trade");
        transactionLock.lock();
        try {
            Reservation buy = requireReservation(trade.buyOrderId(), OrderSide.BUY);
            Reservation sell = requireReservation(trade.sellOrderId(), OrderSide.SELL);
            validateReservation(buy, trade);
            validateReservation(sell, trade);

            Portfolio buyer = requirePortfolio(trade.buyerTraderId());
            Portfolio seller = requirePortfolio(trade.sellerTraderId());
            BigDecimal amount = trade.notional();
            Portfolio updatedBuyer = buyer.buy(trade.symbol(), trade.quantity(), amount);
            Portfolio updatedSeller = seller.sell(trade.symbol(), trade.quantity(), amount);
            portfolios.put(updatedBuyer.traderId(), updatedBuyer);
            portfolios.put(updatedSeller.traderId(), updatedSeller);
            consume(buy, trade.quantity());
            consume(sell, trade.quantity());
        } finally {
            transactionLock.unlock();
        }
    }

    @Override
    public int activeReservationCount() {
        transactionLock.lock();
        try {
            return reservations.size();
        } finally {
            transactionLock.unlock();
        }
    }

    private void consume(Reservation reservation, long quantity) {
        long remaining = reservation.quantity() - quantity;
        if (remaining == 0) {
            reservations.remove(reservation.orderId());
        } else {
            reservations.put(reservation.orderId(), reservation.withQuantity(remaining));
        }
    }

    private void validateReservation(Reservation reservation, Trade trade) {
        if (!reservation.traderId().equals(
                        reservation.side() == OrderSide.BUY
                                ? trade.buyerTraderId()
                                : trade.sellerTraderId())
                || !reservation.symbol().equals(trade.symbol())
                || reservation.quantity() < trade.quantity()) {
            throw new OrderStateException("trade does not match reservation for order "
                    + reservation.orderId());
        }
    }

    private Reservation requireReservation(long orderId, OrderSide side) {
        Reservation reservation = reservations.get(orderId);
        if (reservation == null || reservation.side() != side) {
            throw new OrderStateException("missing " + side + " reservation for order " + orderId);
        }
        return reservation;
    }

    private Portfolio requirePortfolio(String traderId) {
        Portfolio portfolio = portfolios.get(traderId);
        if (portfolio == null) {
            throw new IllegalArgumentException("portfolio does not exist: " + traderId);
        }
        return portfolio;
    }

    private BigDecimal reservedCash(String traderId) {
        return reservations.values().stream()
                .filter(reservation -> reservation.side() == OrderSide.BUY
                        && reservation.traderId().equals(traderId))
                .map(reservation -> reservation.limitPrice()
                        .multiply(BigDecimal.valueOf(reservation.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private long reservedPosition(String traderId, String symbol) {
        return reservations.values().stream()
                .filter(reservation -> reservation.side() == OrderSide.SELL
                        && reservation.traderId().equals(traderId)
                        && reservation.symbol().equals(symbol))
                .mapToLong(Reservation::quantity)
                .sum();
    }

    private void requireNoReservations(String traderId) {
        boolean hasReservations = reservations.values().stream()
                .anyMatch(reservation -> reservation.traderId().equals(traderId));
        if (hasReservations) {
            throw new OrderStateException("portfolio has active order reservations: " + traderId);
        }
    }

    private static String normalizeTraderId(String traderId) {
        return DomainValidation.identifier(traderId, "traderId");
    }

    private record Reservation(
            long orderId,
            String traderId,
            String symbol,
            OrderSide side,
            long quantity,
            BigDecimal limitPrice) {

        private static Reservation from(Order order) {
            return new Reservation(
                    order.id(),
                    order.traderId(),
                    order.symbol(),
                    order.side(),
                    order.remainingQuantity(),
                    order.limitPrice());
        }

        private Reservation withQuantity(long newQuantity) {
            return new Reservation(orderId, traderId, symbol, side, newQuantity, limitPrice);
        }
    }
}
