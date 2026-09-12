package com.example.inventory.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.inventory.inventory.domain.InventoryItem;
import com.example.inventory.inventory.repository.InventoryItemRepository;
import com.example.inventory.stock.repository.StockMovementRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

class InventoryReconciliationServiceTest {

    @Test
    void handlesAnEmptyInventoryWithoutIssuingAnEmptyInQuery() {
        InventoryItemRepository itemRepository = mock(InventoryItemRepository.class);
        StockMovementRepository movementRepository = mock(StockMovementRepository.class);
        when(itemRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        var service = new InventoryReconciliationService(
                itemRepository, movementRepository, new SimpleMeterRegistry());

        var report = service.reconcile(10);

        assertThat(report.scannedItems()).isZero();
        assertThat(report.mismatchCount()).isZero();
        verifyNoInteractions(movementRepository);
    }

    @Test
    void reconcilesEachPageWithOneLedgerAggregateQuery() {
        InventoryItemRepository itemRepository = mock(InventoryItemRepository.class);
        StockMovementRepository movementRepository = mock(StockMovementRepository.class);
        InventoryItem first = item(UUID.randomUUID(), "SKU-1", 8);
        InventoryItem second = item(UUID.randomUUID(), "SKU-2", 5);
        StockMovementRepository.ItemQuantityTotal firstTotal = total(first.getId(), 8);
        StockMovementRepository.ItemQuantityTotal secondTotal = total(second.getId(), 3);
        when(itemRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(first, second)));
        when(movementRepository.sumQuantityDeltaByItemIds(List.of(first.getId(), second.getId())))
                .thenReturn(List.of(firstTotal, secondTotal));

        var service = new InventoryReconciliationService(
                itemRepository, movementRepository, new SimpleMeterRegistry());

        var report = service.reconcile(10);

        assertThat(report.scannedItems()).isEqualTo(2);
        assertThat(report.mismatchCount()).isEqualTo(1);
        assertThat(report.mismatches()).singleElement().satisfies(mismatch -> {
            assertThat(mismatch.itemId()).isEqualTo(second.getId());
            assertThat(mismatch.storedQuantity()).isEqualTo(5);
            assertThat(mismatch.ledgerQuantity()).isEqualTo(3);
        });
        verify(movementRepository, times(1)).sumQuantityDeltaByItemIds(any());
    }

    private InventoryItem item(UUID id, String sku, long quantity) {
        InventoryItem item = mock(InventoryItem.class);
        when(item.getId()).thenReturn(id);
        when(item.getSku()).thenReturn(sku);
        when(item.getQuantity()).thenReturn(quantity);
        return item;
    }

    private StockMovementRepository.ItemQuantityTotal total(UUID itemId, long quantity) {
        StockMovementRepository.ItemQuantityTotal total = mock(StockMovementRepository.ItemQuantityTotal.class);
        when(total.getItemId()).thenReturn(itemId);
        when(total.getQuantity()).thenReturn(quantity);
        return total;
    }
}
