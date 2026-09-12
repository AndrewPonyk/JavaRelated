package com.example.inventory.scheduling;

import com.example.inventory.inventory.repository.InventoryItemRepository;
import com.example.inventory.stock.repository.StockMovementRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryReconciliationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(InventoryReconciliationService.class);
    private final InventoryItemRepository itemRepository;
    private final StockMovementRepository movementRepository;
    private final Counter mismatchCounter;

    public InventoryReconciliationService(InventoryItemRepository itemRepository,
                                          StockMovementRepository movementRepository,
                                          MeterRegistry meterRegistry) {
        this.itemRepository = itemRepository;
        this.movementRepository = movementRepository;
        this.mismatchCounter = Counter.builder("inventory.reconciliation.mismatches")
                .description("Inventory items whose on-hand quantity differs from their movement ledger")
                .register(meterRegistry);
    }

    @Transactional(readOnly = true)
    public ReconciliationReport reconcile() {
        return reconcile(1000);
    }

    @Transactional(readOnly = true)
    public ReconciliationReport reconcile(int maxResults) {
        List<Mismatch> mismatches = new ArrayList<>();
        long scanned = 0;
        long mismatchCount = 0;
        int pageNumber = 0;
        while (true) {
            var page = itemRepository.findAll(PageRequest.of(pageNumber, 250, Sort.by("id")));
            List<UUID> itemIds = page.getContent().stream().map(item -> item.getId()).toList();
            Map<UUID, Long> ledgerQuantities = itemIds.isEmpty() ? Map.of() : movementRepository
                    .sumQuantityDeltaByItemIds(itemIds).stream()
                    .collect(Collectors.toMap(StockMovementRepository.ItemQuantityTotal::getItemId,
                            StockMovementRepository.ItemQuantityTotal::getQuantity));
            for (var item : page) {
                scanned++;
                long ledgerQuantity = ledgerQuantities.getOrDefault(item.getId(), 0L);
                if (ledgerQuantity != item.getQuantity()) {
                    mismatchCount++;
                    Mismatch mismatch = new Mismatch(item.getId(), item.getSku(),
                            item.getQuantity(), ledgerQuantity);
                    if (mismatches.size() < maxResults) {
                        mismatches.add(mismatch);
                    }
                    mismatchCounter.increment();
                    LOGGER.error("Inventory ledger mismatch: {}", mismatch);
                }
            }
            if (page.isLast()) {
                return new ReconciliationReport(scanned, mismatchCount,
                        mismatchCount > mismatches.size(), List.copyOf(mismatches));
            }
            pageNumber++;
        }
    }

    public record Mismatch(UUID itemId, String sku, long storedQuantity, long ledgerQuantity) {
    }

    public record ReconciliationReport(long scannedItems, long mismatchCount, boolean truncated,
                                       List<Mismatch> mismatches) {
    }
}
