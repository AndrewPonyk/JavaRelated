package com.example.inventory.forecast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.inventory.common.error.NotFoundException;
import com.example.inventory.inventory.domain.InventoryItem;
import com.example.inventory.inventory.domain.Warehouse;
import com.example.inventory.inventory.repository.InventoryItemRepository;
import com.example.inventory.stock.domain.MovementType;
import com.example.inventory.stock.domain.StockMovement;
import com.example.inventory.stock.repository.StockMovementRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ForecastServiceTest {

    @Mock private ForecastSnapshotRepository forecastRepository;
    @Mock private InventoryItemRepository itemRepository;
    @Mock private StockMovementRepository movementRepository;
    @Mock private ForecastClient forecastClient;
    private ForecastService service;
    private InventoryItem item;
    private UUID itemId;

    @BeforeEach
    void setUp() {
        service = new ForecastService(forecastRepository, itemRepository, movementRepository, forecastClient);
        Warehouse warehouse = new Warehouse("FC", "Forecast warehouse");
        ReflectionTestUtils.setField(warehouse, "id", UUID.randomUUID());
        item = new InventoryItem(warehouse, "FORECAST-1", "4006381333931", "Forecast item", 12, 3);
        itemId = UUID.randomUUID();
        ReflectionTestUtils.setField(item, "id", itemId);
    }

    @Test
    void refreshAggregatesDemandAndPersistsPredictionMetadata() {
        StockMovement shipment = new StockMovement(item, null, MovementType.SHIPMENT, -3, 12, 9,
                "Shipment", "ORDER", "1", "tester", "key");
        StockMovement ignoredReceipt = new StockMovement(item, null, MovementType.RECEIPT, 5, 9, 14,
                "Receipt", "PO", "2", "tester", "key-2");
        ReflectionTestUtils.setField(shipment, "occurredAt", Instant.now());
        ReflectionTestUtils.setField(ignoredReceipt, "occurredAt", Instant.now());
        Instant generatedAt = Instant.parse("2026-08-20T12:00:00Z");
        ForecastClient.Prediction prediction = new ForecastClient.Prediction("FORECAST-1",
                List.of(1.25, 2.25), List.of(0.5, 1.0), List.of(2.0, 3.0),
                "holt-v2", "daily-demand-v1", 0.75, generatedAt);
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(movementRepository.findDemandHistory(any(), any())).thenReturn(List.of(shipment, ignoredReceipt));
        when(forecastClient.predict(anyString(), anyList(), anyInt())).thenAnswer(invocation -> {
            List<Double> history = invocation.getArgument(1);
            assertThat(history).hasSize(90);
            assertThat(history.get(history.size() - 1)).isEqualTo(3.0);
            return prediction;
        });
        when(forecastRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            ForecastSnapshot snapshot = invocation.getArgument(0);
            ReflectionTestUtils.setField(snapshot, "id", UUID.randomUUID());
            return snapshot;
        });

        ForecastResponse response = service.refresh(itemId, 2);

        assertThat(response.predictedDemand()).isEqualByComparingTo("3.5000");
        assertThat(response.lowerBound()).isEqualByComparingTo("1.5000");
        assertThat(response.upperBound()).isEqualByComparingTo("5.0000");
        assertThat(response.modelMae()).isEqualByComparingTo("0.7500");
        assertThat(response.generatedAt()).isEqualTo(generatedAt);
    }

    @Test
    void latestMapsSnapshotAndReportsMissingForecast() {
        ForecastSnapshot snapshot = new ForecastSnapshot(item, 7,
                new java.math.BigDecimal("10.0000"), new java.math.BigDecimal("8.0000"),
                new java.math.BigDecimal("12.0000"), "model", "features", null, Instant.now());
        ReflectionTestUtils.setField(snapshot, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(snapshot, "createdAt", Instant.now());
        when(forecastRepository.findFirstByItemIdOrderByGeneratedAtDesc(itemId))
                .thenReturn(Optional.of(snapshot), Optional.empty());

        assertThat(service.latest(itemId).sku()).isEqualTo("FORECAST-1");
        assertThat(snapshot.getCreatedAt()).isNotNull();
        assertThatThrownBy(() -> service.latest(itemId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("No forecast");
    }

    @Test
    void refreshRejectsMissingItem() {
        when(itemRepository.findById(itemId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.refresh(itemId, 7))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Inventory item was not found.");
    }

    @Test
    void refreshAllPagesAndSkipsInactiveItems() {
        InventoryItem inactive = new InventoryItem(item.getWarehouse(), "INACTIVE", "5901234123457",
                "Inactive", 0, 0);
        inactive.deactivate();
        ReflectionTestUtils.setField(inactive, "id", UUID.randomUUID());
        InventoryItem secondActive = new InventoryItem(item.getWarehouse(), "FORECAST-2", "9780201379624",
                "Second", 1, 0);
        ReflectionTestUtils.setField(secondActive, "id", UUID.randomUUID());
        when(itemRepository.findAll(PageRequest.of(0, 100))).thenReturn(
                new PageImpl<>(List.of(item, inactive), PageRequest.of(0, 100), 101));
        when(itemRepository.findAll(PageRequest.of(1, 100))).thenReturn(
                new PageImpl<>(List.of(secondActive), PageRequest.of(1, 100), 101));
        ForecastService subject = spy(service);
        doReturn(new ForecastResponse(null, null, "x", 3, null, null, null,
                "m", "f", null, Instant.now()))
                .when(subject).refresh(any(), anyInt());

        assertThat(subject.refreshAll(3)).isEqualTo(2);
        verify(subject).refresh(itemId, 3);
        verify(subject).refresh(secondActive.getId(), 3);
    }
}
