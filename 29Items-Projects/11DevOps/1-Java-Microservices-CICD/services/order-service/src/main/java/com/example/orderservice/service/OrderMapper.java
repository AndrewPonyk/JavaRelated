package com.example.orderservice.service;

import com.example.orderservice.api.dto.CreateOrderRequest;
import com.example.orderservice.api.dto.OrderItemResponse;
import com.example.orderservice.api.dto.OrderResponse;
import com.example.orderservice.api.dto.OrderSummaryResponse;
import com.example.orderservice.domain.Order;
import com.example.orderservice.domain.OrderItem;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Structural DTO ⇄ entity mapping. Deliberately hand-written (no MapStruct/reflection):
 * the model is small, and explicit code keeps coverage measurable and refactors safe.
 */
@Component
public class OrderMapper {

    static final String DEFAULT_CURRENCY = "USD";

    public Order toEntity(CreateOrderRequest request) {
        // Locale.ROOT: default-locale upper-casing corrupts codes (Turkish "inr" -> "İNR")
        String currency = request.currency() == null ? DEFAULT_CURRENCY : request.currency().toUpperCase(Locale.ROOT);
        Order order = new Order(request.customerId(), currency);
        request.items().forEach(item ->
                order.addItem(new OrderItem(item.sku(), item.productName(), item.quantity(), item.unitPrice())));
        return order;
    }

    public OrderResponse toResponse(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getStatus(),
                order.getCurrency(),
                order.getTotalAmount(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                order.getItems().stream().map(this::toItemResponse).toList());
    }

    public OrderSummaryResponse toSummary(Order order) {
        return new OrderSummaryResponse(
                order.getId(),
                order.getCustomerId(),
                order.getStatus(),
                order.getCurrency(),
                order.getTotalAmount(),
                order.getCreatedAt());
    }

    private OrderItemResponse toItemResponse(OrderItem item) {
        return new OrderItemResponse(
                item.getId(),
                item.getSku(),
                item.getProductName(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.lineTotal());
    }
}
