package com.example.trading;

import com.example.trading.application.OrderService;
import com.example.trading.application.PortfolioService;
import com.example.trading.application.PriceService;
import com.example.trading.application.ProfitCalculationService;
import com.example.trading.application.TradeService;
import com.example.trading.application.TradingSimulator;
import com.example.trading.infrastructure.concurrent.AtomicOrderIdGenerator;
import com.example.trading.infrastructure.concurrent.ConcurrentAuditLog;
import com.example.trading.infrastructure.concurrent.ConcurrentPortfolioRepository;
import com.example.trading.infrastructure.concurrent.ConcurrentTradeRepository;
import com.example.trading.infrastructure.concurrent.InMemoryOrderBook;
import com.example.trading.infrastructure.concurrent.StampedPriceCache;
import com.example.trading.infrastructure.marketdata.SyntheticPriceProvider;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

public final class TestFixture {
    public static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    private TestFixture() {
    }

    public static Context context() {
        ConcurrentPortfolioRepository portfolios = new ConcurrentPortfolioRepository();
        ConcurrentTradeRepository trades = new ConcurrentTradeRepository();
        ConcurrentAuditLog audit = new ConcurrentAuditLog();
        ProfitCalculationService profits = new ProfitCalculationService(portfolios, 2);
        PortfolioService portfolioService = new PortfolioService(portfolios, audit, CLOCK);
        PriceService priceService = new PriceService(
                new StampedPriceCache(),
                new SyntheticPriceProvider(),
                Runnable::run,
                Duration.ofSeconds(1),
                audit,
                CLOCK);
        OrderService orderService = new OrderService(
                new InMemoryOrderBook(CLOCK, true),
                new AtomicOrderIdGenerator(),
                portfolios,
                trades,
                audit,
                CLOCK);
        TradingSimulator simulator = new TradingSimulator(
                orderService,
                portfolioService,
                new TradeService(trades),
                priceService,
                profits,
                audit,
                new BigDecimal("25"));
        return new Context(simulator, portfolios, trades, audit, profits);
    }

    public record Context(
            TradingSimulator simulator,
            ConcurrentPortfolioRepository portfolios,
            ConcurrentTradeRepository trades,
            ConcurrentAuditLog audit,
            ProfitCalculationService profits)
            implements AutoCloseable {

        @Override
        public void close() {
            profits.close();
        }
    }
}
