package com.example.trading.presentation.cli;

import com.example.trading.application.OrderService;
import com.example.trading.application.PlaceOrderCommand;
import com.example.trading.application.PortfolioService;
import com.example.trading.application.PriceService;
import com.example.trading.application.ProfitCalculationService;
import com.example.trading.application.SimulationReport;
import com.example.trading.application.TradeService;
import com.example.trading.application.TradingSimulator;
import com.example.trading.domain.Order;
import com.example.trading.domain.OrderSide;
import com.example.trading.domain.Portfolio;
import com.example.trading.infrastructure.concurrent.AtomicOrderIdGenerator;
import com.example.trading.infrastructure.concurrent.ConcurrentAuditLog;
import com.example.trading.infrastructure.concurrent.ConcurrentPortfolioRepository;
import com.example.trading.infrastructure.concurrent.ConcurrentTradeRepository;
import com.example.trading.infrastructure.concurrent.InMemoryOrderBook;
import com.example.trading.infrastructure.concurrent.StampedPriceCache;
import com.example.trading.infrastructure.config.SimulatorConfig;
import com.example.trading.infrastructure.marketdata.SyntheticPriceProvider;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SplittableRandom;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class SimulatorApplication {
    private static final System.Logger LOGGER = System.getLogger(SimulatorApplication.class.getName());
    private static final List<String> SYMBOLS = List.of("AAPL", "MSFT", "NVDA");

    private SimulatorApplication() {
    }

    public static void main(String[] args) {
        int exitCode = execute();
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int execute() {
        try {
            run(SimulatorConfig.fromEnvironment(), System.out);
            return 0;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOGGER.log(System.Logger.Level.ERROR, "Simulation interrupted", exception);
            return 130;
        } catch (IllegalArgumentException exception) {
            LOGGER.log(System.Logger.Level.ERROR, "Invalid simulation configuration", exception);
            return 2;
        } catch (ExecutionException | TimeoutException | RuntimeException exception) {
            LOGGER.log(System.Logger.Level.ERROR, "Simulation failed", exception);
            return 1;
        }
    }

    public static SimulationReport run(SimulatorConfig config, PrintStream output)
            throws InterruptedException, ExecutionException, TimeoutException {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(output, "output");
        Clock clock = Clock.systemUTC();
        ConcurrentPortfolioRepository portfolioRepository = new ConcurrentPortfolioRepository();
        ConcurrentTradeRepository tradeRepository = new ConcurrentTradeRepository();
        ConcurrentAuditLog auditLog = new ConcurrentAuditLog();
        SyntheticPriceProvider provider = new SyntheticPriceProvider();
        Instant started = Instant.now(clock);

        try (ExecutorService clients = Executors.newVirtualThreadPerTaskExecutor();
                ProfitCalculationService profitService =
                        new ProfitCalculationService(portfolioRepository, config.profitParallelism())) {
            PortfolioService portfolioService =
                    new PortfolioService(portfolioRepository, auditLog, clock);
            seedPortfolios(config, portfolioService);
            PriceService priceService = new PriceService(
                    new StampedPriceCache(),
                    provider,
                    clients,
                    config.priceTimeout(),
                    auditLog,
                    clock);
            OrderService orderService = new OrderService(
                    new InMemoryOrderBook(clock, true),
                    new AtomicOrderIdGenerator(),
                    portfolioRepository,
                    tradeRepository,
                    auditLog,
                    clock);
            TradingSimulator simulator = new TradingSimulator(
                    orderService,
                    portfolioService,
                    new TradeService(tradeRepository),
                    priceService,
                    profitService,
                    auditLog,
                    config.maximumPriceDeviationPercent());

            output.printf(
                    "Loading: simulating %,d traders with virtual threads (%s environment)%n",
                    config.traderCount(),
                    config.environment());
            List<TimedCommand> commands = commands(config, provider.snapshot());
            List<Future<TimedOrder>> results = new ArrayList<>(commands.size());
            for (TimedCommand command : commands) {
                results.add(clients.submit(() -> execute(simulator, command.command())));
            }

            List<Long> latencies = await(results, config.simulationTimeout());
            Map<String, BigDecimal> prices = provider.snapshot();
            BigDecimal initialCapital = initialCapital(config, prices);
            BigDecimal aggregateProfit =
                    simulator.calculateAggregateProfit(prices, initialCapital);
            List<Order> orders = simulator.listOrders();
            int activeOrders = Math.toIntExact(orders.stream().filter(Order::isActive).count());
            SimulationReport report = new SimulationReport(
                    config.traderCount(),
                    orders.size(),
                    simulator.listTrades().size(),
                    activeOrders,
                    aggregateProfit,
                    Duration.between(started, Instant.now(clock)),
                    percentile(latencies, 95));
            printReport(output, report);
            return report;
        }
    }

    private static void seedPortfolios(
            SimulatorConfig config,
            PortfolioService portfolioService) {
        Map<String, Long> positions = Map.of(
                "AAPL", config.initialPosition(),
                "MSFT", config.initialPosition(),
                "NVDA", config.initialPosition());
        for (int index = 0; index < config.traderCount(); index++) {
            portfolioService.create(new Portfolio(
                    "trader-" + index,
                    config.initialCash(),
                    positions));
        }
    }

    private static List<TimedCommand> commands(
            SimulatorConfig config,
            Map<String, BigDecimal> prices) {
        SplittableRandom random = new SplittableRandom(config.randomSeed());
        List<TimedCommand> commands = new ArrayList<>(config.traderCount());
        for (int index = 0; index < config.traderCount(); index++) {
            String symbol = SYMBOLS.get(Math.floorMod(index, SYMBOLS.size()));
            PlaceOrderCommand command = new PlaceOrderCommand(
                    "trader-" + index,
                    symbol,
                    index % 2 == 0 ? OrderSide.BUY : OrderSide.SELL,
                    random.nextLong(1L, 11L),
                    prices.get(symbol));
            commands.add(new TimedCommand(command));
        }
        return List.copyOf(commands);
    }

    private static TimedOrder execute(
            TradingSimulator simulator,
            PlaceOrderCommand command) {
        long started = System.nanoTime();
        Order order = simulator.placeOrder(command).join();
        return new TimedOrder(order, System.nanoTime() - started);
    }

    private static List<Long> await(
            List<Future<TimedOrder>> futures,
            Duration timeout)
            throws InterruptedException, ExecutionException, TimeoutException {
        long deadline = System.nanoTime() + timeout.toNanos();
        List<Long> latencies = new ArrayList<>(futures.size());
        try {
            for (Future<TimedOrder> future : futures) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    throw new TimeoutException("simulation exceeded " + timeout);
                }
                latencies.add(future.get(remaining, TimeUnit.NANOSECONDS).latencyNanos());
            }
            return latencies;
        } catch (ExecutionException | InterruptedException | TimeoutException exception) {
            futures.forEach(future -> future.cancel(true));
            throw exception;
        }
    }

    private static BigDecimal initialCapital(
            SimulatorConfig config,
            Map<String, BigDecimal> prices) {
        BigDecimal positionValue = prices.values().stream()
                .map(price -> price.multiply(BigDecimal.valueOf(config.initialPosition())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return config.initialCash().add(positionValue);
    }

    private static Duration percentile(List<Long> values, int percentile) {
        if (values.isEmpty()) {
            return Duration.ZERO;
        }
        List<Long> sorted = values.stream().sorted(Comparator.naturalOrder()).toList();
        int index = Math.min(
                sorted.size() - 1,
                Math.max(0, (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1));
        return Duration.ofNanos(sorted.get(index));
    }

    private static void printReport(PrintStream output, SimulationReport report) {
        output.printf("Complete: %,d orders accepted.%n", report.orderCount());
        output.printf("Trades executed: %,d.%n", report.tradeCount());
        output.printf("Active orders: %,d.%n", report.activeOrderCount());
        output.printf("Aggregate marked profit: %s.%n", report.aggregateProfit().toPlainString());
        output.printf("Elapsed: %d ms; p95 order latency: %d ms.%n",
                report.elapsed().toMillis(),
                report.p95OrderLatency().toMillis());
    }

    private record TimedCommand(PlaceOrderCommand command) {
    }

    private record TimedOrder(Order order, long latencyNanos) {
    }
}
