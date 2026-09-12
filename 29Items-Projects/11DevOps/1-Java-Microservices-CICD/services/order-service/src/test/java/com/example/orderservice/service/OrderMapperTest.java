package com.example.orderservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.orderservice.api.dto.CreateOrderRequest;
import com.example.orderservice.api.dto.OrderItemRequest;
import com.example.orderservice.api.dto.OrderResponse;
import com.example.orderservice.api.dto.OrderSummaryResponse;
import com.example.orderservice.domain.Order;
import com.example.orderservice.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderMapperTest {

    private static final UUID CUSTOMER = UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7");

    private final OrderMapper mapper = new OrderMapper();

    private CreateOrderRequest request(String currency) {
        return new CreateOrderRequest(CUSTOMER, currency, List.of(
                new OrderItemRequest("SKU-1001", "Mechanical Keyboard", 1, new BigDecimal("89.90")),
                new OrderItemRequest("SKU-2002", "USB-C Cable", 2, new BigDecimal("9.99"))));
    }

    @Test
    void toEntityBuildsTheAggregateInStatusNew() {
        Order order = mapper.toEntity(request("eur"));

        assertThat(order.getCustomerId()).isEqualTo(CUSTOMER);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.NEW);
        assertThat(order.getCurrency()).isEqualTo("EUR");
        assertThat(order.getItems()).hasSize(2);
        assertThat(order.getItems().getFirst().getSku()).isEqualTo("SKU-1001");
    }

    @Test
    void toEntityDefaultsCurrencyToUsd() {
        Order order = mapper.toEntity(request(null));

        assertThat(order.getCurrency()).isEqualTo(OrderMapper.DEFAULT_CURRENCY);
    }

    @Test
    void toResponseMapsEveryFieldIncludingLineTotals() {
        Order order = mapper.toEntity(request("USD"));
        order.recalculateTotal();
        order.setCreatedAt(Instant.parse("2026-02-01T12:00:00Z"));
        order.setUpdatedAt(Instant.parse("2026-02-01T12:34:56Z"));

        OrderResponse response = mapper.toResponse(order);

        assertThat(response.id()).isEqualTo(order.getId());
        assertThat(response.customerId()).isEqualTo(CUSTOMER);
        assertThat(response.status()).isEqualTo(OrderStatus.NEW);
        assertThat(response.totalAmount()).isEqualByComparingTo("109.88");
        assertThat(response.createdAt()).isEqualTo(Instant.parse("2026-02-01T12:00:00Z"));
        assertThat(response.updatedAt()).isEqualTo(Instant.parse("2026-02-01T12:34:56Z"));
        assertThat(response.items()).hasSize(2);
        assertThat(response.items().get(1).lineTotal()).isEqualByComparingTo("19.98");
    }

    @Test
    void toSummaryProjectsWithoutItems() {
        Order order = mapper.toEntity(request("USD"));
        order.recalculateTotal();
        order.setCreatedAt(Instant.parse("2026-02-01T12:00:00Z"));

        OrderSummaryResponse summary = mapper.toSummary(order);

        assertThat(summary.id()).isEqualTo(order.getId());
        assertThat(summary.status()).isEqualTo(OrderStatus.NEW);
        assertThat(summary.totalAmount()).isEqualByComparingTo("109.88");
        assertThat(summary.createdAt()).isEqualTo(Instant.parse("2026-02-01T12:00:00Z"));
    }
}
