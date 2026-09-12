package com.example.inventory.stock.dto;

import com.example.inventory.stock.domain.BarcodeSymbology;
import java.util.UUID;

public record BarcodeResponse(
        UUID id,
        String barcode,
        BarcodeSymbology symbology,
        boolean primary) {
}

