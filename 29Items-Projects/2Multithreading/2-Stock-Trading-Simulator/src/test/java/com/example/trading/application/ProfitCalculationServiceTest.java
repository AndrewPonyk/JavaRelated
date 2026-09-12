package com.example.trading.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.trading.domain.Portfolio;
import com.example.trading.infrastructure.concurrent.ConcurrentPortfolioRepository;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProfitCalculationServiceTest {

    @Test
    void calculatesAggregateMarkedProfitInTheDedicatedPool() {
        ConcurrentPortfolioRepository repository = new ConcurrentPortfolioRepository();
        repository.create(new Portfolio(
                "trader-1",
                new BigDecimal("900.00"),
                Map.of("AAPL", 1L)));
        repository.create(new Portfolio(
                "trader-2",
                new BigDecimal("1000.00"),
                Map.of()));

        try (ProfitCalculationService service = new ProfitCalculationService(repository, 2)) {
            BigDecimal profit = service.calculateAggregateProfit(
                    Map.of("AAPL", new BigDecimal("110.00")),
                    new BigDecimal("1000.00"));

            assertEquals(new BigDecimal("10.00"), profit);
        }
    }
}
