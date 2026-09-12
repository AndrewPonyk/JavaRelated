package com.example.inventory.stock.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record TransferRequest(
        @NotNull UUID sourceItemId,
        @NotNull UUID destinationWarehouseId,
        @Positive long quantity,
        @NotBlank @Size(max = 250) String reason,
        @Size(max = 128) String reference) {
}

