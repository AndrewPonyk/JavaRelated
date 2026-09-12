package com.example.orderservice.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** One order line in a {@link CreateOrderRequest}. Size limits mirror the DB columns (V1). */
public record OrderItemRequest(
        @NotBlank @Size(max = 64) String sku,
        @NotBlank @Size(max = 255) String productName,
        @Min(value = 1, message = "quantity must be at least 1") int quantity,
        @NotNull @DecimalMin(value = "0.00") @Digits(integer = 15, fraction = 4) BigDecimal unitPrice) {
}
