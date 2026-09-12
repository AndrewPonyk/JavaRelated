package com.example.inventory.stock.dto;

import com.example.inventory.inventory.dto.InventoryResponse;
import java.util.List;

public record BulkAdjustmentResponse(List<InventoryResponse> items) {
}

