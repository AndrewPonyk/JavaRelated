package com.example.inventory.inventory.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.inventory.audit.AuditService;
import com.example.inventory.common.error.NotFoundException;
import com.example.inventory.common.web.RequestContext;
import com.example.inventory.idempotency.IdempotencyService;
import com.example.inventory.inventory.repository.InventoryItemRepository;
import com.example.inventory.inventory.repository.WarehouseRepository;
import com.example.inventory.outbox.OutboxService;
import com.example.inventory.stock.repository.ItemBarcodeRepository;
import com.example.inventory.stock.repository.ReservationRepository;
import com.example.inventory.stock.repository.StockMovementRepository;
import com.example.inventory.stock.service.BarcodeValidator;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {
    @Mock private InventoryItemRepository itemRepository;
    @Mock private WarehouseRepository warehouseRepository;
    @Mock private ItemBarcodeRepository barcodeRepository;
    @Mock private StockMovementRepository movementRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private IdempotencyService idempotencyService;
    @Mock private OutboxService outboxService;
    @Mock private AuditService auditService;
    @Mock private RequestContext requestContext;
    private InventoryService inventoryService;

    @BeforeEach
    void setUp() {
        inventoryService = new InventoryService(itemRepository, warehouseRepository, barcodeRepository,
                movementRepository, reservationRepository, new BarcodeValidator(), idempotencyService,
                outboxService, auditService, requestContext);
    }

    @Test
    void getThrowsNotFoundWhenItemDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(itemRepository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> inventoryService.get(id))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Inventory item was not found.");
        verify(itemRepository).findById(id);
    }

    @Test
    void barcodeLookupReportsMissingBarcode() {
        when(barcodeRepository.findByBarcode("MISSING")).thenReturn(java.util.List.of());
        assertThatThrownBy(() -> inventoryService.findByBarcode(" missing ", null))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("barcode");
    }
}
