package com.example.inventory.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ItemDeactivateRequest(
        @PositiveOrZero long version,
        @NotBlank @Size(max = 250) String reason) {
}

