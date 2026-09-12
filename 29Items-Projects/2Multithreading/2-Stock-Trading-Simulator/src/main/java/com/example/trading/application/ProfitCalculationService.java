package com.example.trading.application;

import com.example.trading.application.port.PortfolioRepository;
import com.example.trading.domain.Portfolio;
import com.example.trading.domain.DomainValidation;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

public final class ProfitCalculationService implements AutoCloseable {
    private static final int SEQUENTIAL_THRESHOLD = 128;

    private final PortfolioRepository repository;
    private final ForkJoinPool pool;
    private final boolean ownsPool;

    public ProfitCalculationService(PortfolioRepository repository, int parallelism) {
        this(repository, new ForkJoinPool(requirePositive(parallelism)), true);
    }

    ProfitCalculationService(PortfolioRepository repository, ForkJoinPool pool, boolean ownsPool) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.pool = Objects.requireNonNull(pool, "pool");
        this.ownsPool = ownsPool;
    }

    public BigDecimal calculateAggregateProfit(
            Map<String, BigDecimal> marketPrices,
            BigDecimal initialCapitalPerPortfolio) {
        Map<String, BigDecimal> prices = Map.copyOf(Objects.requireNonNull(marketPrices, "marketPrices"));
        BigDecimal initialCapital = DomainValidation.money(
                initialCapitalPerPortfolio, "initialCapitalPerPortfolio", true);
        prices.forEach((symbol, price) -> {
            DomainValidation.symbol(symbol, "market price symbol");
            DomainValidation.money(price, "market price", false);
        });
        List<Portfolio> portfolios = repository.findAll();
        return pool.invoke(new ProfitTask(portfolios, prices, initialCapital, 0, portfolios.size()));
    }

    @Override
    public void close() {
        if (ownsPool) {
            pool.shutdown();
        }
    }

    private static int requirePositive(int value) {
        if (value <= 0) {
            throw new IllegalArgumentException("parallelism must be positive");
        }
        return value;
    }

    private static final class ProfitTask extends RecursiveTask<BigDecimal> {
        private final List<Portfolio> portfolios;
        private final Map<String, BigDecimal> prices;
        private final BigDecimal initialCapital;
        private final int start;
        private final int end;

        private ProfitTask(
                List<Portfolio> portfolios,
                Map<String, BigDecimal> prices,
                BigDecimal initialCapital,
                int start,
                int end) {
            this.portfolios = portfolios;
            this.prices = prices;
            this.initialCapital = initialCapital;
            this.start = start;
            this.end = end;
        }

        @Override
        protected BigDecimal compute() {
            if (end - start <= SEQUENTIAL_THRESHOLD) {
                BigDecimal result = BigDecimal.ZERO;
                for (int index = start; index < end; index++) {
                    result = result.add(profit(portfolios.get(index), prices, initialCapital));
                }
                return result;
            }

            int middle = (start + end) >>> 1;
            ProfitTask left = new ProfitTask(portfolios, prices, initialCapital, start, middle);
            ProfitTask right = new ProfitTask(portfolios, prices, initialCapital, middle, end);
            left.fork();
            BigDecimal rightResult = right.compute();
            return left.join().add(rightResult);
        }

        private static BigDecimal profit(
                Portfolio portfolio,
                Map<String, BigDecimal> prices,
                BigDecimal initialCapital) {
            BigDecimal markedValue = portfolio.cash();
            for (Map.Entry<String, Long> position : portfolio.positions().entrySet()) {
                BigDecimal price = prices.get(position.getKey());
                if (price == null) {
                    throw new IllegalArgumentException("missing price for " + position.getKey());
                }
                markedValue = markedValue.add(price.multiply(BigDecimal.valueOf(position.getValue())));
            }
            return markedValue.subtract(initialCapital);
        }
    }
}
