package com.example.inventory.stock.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record BulkAdjustmentRequest(
        @NotEmpty @Size(max = 100) List<@Valid Entry> entries) {

    public record Entry(
            UUID itemId,
            long delta,
            @Size(min = 1, max = 250) String reason,
            @Size(max = 128) String reference) {
        public Entry {
            if (itemId == null || delta == 0) {
                throw new IllegalArgumentException("itemId is required and delta must not be zero");
            }
        }
    }
}
