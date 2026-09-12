package com.example.inventory.stock.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ReservationCreateRequest(
        @Positive long quantity,
        @NotBlank @Size(max = 128) String externalReference,
        @NotBlank @Size(max = 250) String reason) {
}

