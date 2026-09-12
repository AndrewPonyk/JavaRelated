package com.example.inventory.stock.dto;

import com.example.inventory.stock.domain.BarcodeSymbology;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BarcodeRequest(
        @NotBlank @Size(max = 128) String barcode,
        @NotNull BarcodeSymbology symbology,
        boolean primary) {
}

