package com.example.inventory.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import com.example.inventory.stock.domain.BarcodeSymbology;
import com.example.inventory.stock.dto.BarcodeRequest;
import java.util.List;
import java.util.UUID;

public record InventoryCreateRequest(
        @NotBlank @Size(max = 64) String sku,
        @NotBlank @Size(max = 128) String barcode,
        @NotNull BarcodeSymbology symbology,
        @Size(max = 20) List<@Valid BarcodeRequest> aliases,
        @NotBlank @Size(max = 160) String name,
        @PositiveOrZero long quantity,
        @PositiveOrZero long reorderPoint,
        @NotNull UUID warehouseId) {

    public InventoryCreateRequest {
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
    }
}
