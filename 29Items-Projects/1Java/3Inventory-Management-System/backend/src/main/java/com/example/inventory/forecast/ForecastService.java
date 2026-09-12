package com.example.inventory.forecast;

import com.example.inventory.common.error.NotFoundException;
import com.example.inventory.inventory.domain.InventoryItem;
import com.example.inventory.inventory.repository.InventoryItemRepository;
import com.example.inventory.stock.domain.MovementType;
import com.example.inventory.stock.domain.StockMovement;
import com.example.inventory.stock.repository.StockMovementRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ForecastService {

    private static final EnumSet<MovementType> DEMAND_TYPES = EnumSet.of(
            MovementType.SHIPMENT, MovementType.RESERVATION_FULFILLMENT);
    private final ForecastSnapshotRepository forecastRepository;
    private final InventoryItemRepository itemRepository;
    private final StockMovementRepository movementRepository;
    private final ForecastClient forecastClient;

    public ForecastService(ForecastSnapshotRepository forecastRepository,
                           InventoryItemRepository itemRepository,
                           StockMovementRepository movementRepository,
                           ForecastClient forecastClient) {
        this.forecastRepository = forecastRepository;
        this.itemRepository = itemRepository;
        this.movementRepository = movementRepository;
        this.forecastClient = forecastClient;
    }

    @Transactional(readOnly = true)
    public ForecastResponse latest(UUID itemId) {
        return forecastRepository.findFirstByItemIdOrderByGeneratedAtDesc(itemId)
                .map(this::response)
                .orElseThrow(() -> new NotFoundException("No forecast exists for this item."));
    }

    public ForecastResponse refresh(UUID itemId, int horizonDays) {
        InventoryItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new NotFoundException("Inventory item was not found."));
        List<Double> history = dailyDemand(itemId, 90);
        ForecastClient.Prediction prediction = forecastClient.predict(item.getSku(), history, horizonDays);
        return persist(itemId, horizonDays, prediction);
    }

    public int refreshAll(int horizonDays) {
        int page = 0;
        int refreshed = 0;
        while (true) {
            var items = itemRepository.findAll(PageRequest.of(page, 100));
            for (InventoryItem item : items) {
                if (item.isActive()) {
                    refresh(item.getId(), horizonDays);
                    refreshed++;
                }
            }
            if (items.isLast()) {
                return refreshed;
            }
            page++;
        }
    }

    @Transactional
    protected ForecastResponse persist(UUID itemId, int horizonDays, ForecastClient.Prediction prediction) {
        InventoryItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new NotFoundException("Inventory item was not found."));
        ForecastSnapshot snapshot = new ForecastSnapshot(item, horizonDays,
                sum(prediction.predictions()), sum(prediction.lowerBound()), sum(prediction.upperBound()),
                prediction.modelVersion(), prediction.featureVersion(), decimal(prediction.modelMae()),
                prediction.generatedAt());
        return response(forecastRepository.saveAndFlush(snapshot));
    }

    List<Double> dailyDemand(UUID itemId, int days) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant since = today.minusDays(days - 1L).atStartOfDay().toInstant(ZoneOffset.UTC);
        List<StockMovement> movements = movementRepository.findDemandHistory(itemId, since);
        Map<LocalDate, Long> totals = new LinkedHashMap<>();
        for (int offset = days - 1; offset >= 0; offset--) {
            totals.put(today.minusDays(offset), 0L);
        }
        movements.stream().filter(movement -> DEMAND_TYPES.contains(movement.getType()))
                .forEach(movement -> {
                    LocalDate date = movement.getOccurredAt().atZone(ZoneOffset.UTC).toLocalDate();
                    totals.computeIfPresent(date, (ignored, value) ->
                            value + Math.abs(movement.getQuantityDelta()));
                });
        return totals.values().stream().map(Long::doubleValue).toList();
    }

    private BigDecimal sum(List<Double> values) {
        return values.stream().map(this::decimal).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal decimal(Double value) {
        return value == null ? null : BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP);
    }

    private ForecastResponse response(ForecastSnapshot snapshot) {
        return new ForecastResponse(snapshot.getId(), snapshot.getItem().getId(), snapshot.getItem().getSku(),
                snapshot.getHorizonDays(), snapshot.getPredictedDemand(), snapshot.getLowerBound(),
                snapshot.getUpperBound(), snapshot.getModelVersion(), snapshot.getFeatureVersion(),
                snapshot.getModelMae(), snapshot.getGeneratedAt());
    }
}

