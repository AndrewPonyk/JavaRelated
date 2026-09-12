package com.example.inventory.inventory.dto;

import java.time.Instant;
import java.util.UUID;
import java.util.List;
import com.example.inventory.stock.dto.BarcodeResponse;

public record InventoryResponse(
        UUID id,
        String sku,
        String barcode,
        List<BarcodeResponse> barcodes,
        String name,
        long quantity,
        long reservedQuantity,
        long availableQuantity,
        long reorderPoint,
        boolean lowStock,
        boolean active,
        UUID warehouseId,
        String warehouseCode,
        long version,
        Instant createdAt,
        Instant updatedAt) {
}
