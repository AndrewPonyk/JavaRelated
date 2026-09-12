package com.example.inventory.stock.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record QuantityCommandRequest(
        @Positive long quantity,
        @NotBlank @Size(max = 250) String reason,
        @Size(max = 128) String reference) {
}

