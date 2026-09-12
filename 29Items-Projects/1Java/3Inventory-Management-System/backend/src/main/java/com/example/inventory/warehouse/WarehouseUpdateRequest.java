package com.example.inventory.warehouse;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record WarehouseUpdateRequest(
        @NotBlank @Size(max = 120) String name,
        boolean active,
        @PositiveOrZero long version) {
}

