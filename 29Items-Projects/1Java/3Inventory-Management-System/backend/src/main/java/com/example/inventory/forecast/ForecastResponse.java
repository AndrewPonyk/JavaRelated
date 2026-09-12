package com.example.inventory.forecast;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ForecastResponse(
        UUID id,
        UUID inventoryItemId,
        String sku,
        int horizonDays,
        BigDecimal predictedDemand,
        BigDecimal lowerBound,
        BigDecimal upperBound,
        String modelVersion,
        String featureVersion,
        BigDecimal modelMae,
        Instant generatedAt) {
}

