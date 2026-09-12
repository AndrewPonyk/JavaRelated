package com.example.orderservice.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Order line representation. {@code lineTotal} = quantity × unitPrice. */
public record OrderItemResponse(
        UUID id,
        String sku,
        String productName,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal) {
}
