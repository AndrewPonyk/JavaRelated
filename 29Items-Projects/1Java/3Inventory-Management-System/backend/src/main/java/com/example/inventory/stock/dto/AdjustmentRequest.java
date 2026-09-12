package com.example.inventory.stock.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdjustmentRequest(
        long delta,
        @NotBlank @Size(max = 250) String reason,
        @Size(max = 128) String reference) {

    public AdjustmentRequest {
        if (delta == 0) {
            throw new IllegalArgumentException("delta must not be zero");
        }
    }
}

