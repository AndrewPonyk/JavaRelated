package com.example.inventory.warehouse;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record WarehouseCreateRequest(
        @NotBlank @Size(max = 32) @Pattern(regexp = "[A-Za-z0-9_-]+") String code,
        @NotBlank @Size(max = 120) String name) {
}

