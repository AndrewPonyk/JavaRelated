package com.example.orderservice.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.orderservice.exception.InvalidStatusTransitionException;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderTest {

    private static final UUID CUSTOMER = UUID.fromString("0f8a9c1e-2b3d-4e5f-8a9b-1c2d3e4f5a6b");

    @Test
    void newOrderStartsInNewStatusWithZeroTotal() {
        Order order = new Order(CUSTOMER, "USD");

        assertThat(order.getId()).isNotNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.NEW);
        assertThat(order.getTotalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(order.getItems()).isEmpty();
    }

    @Test
    void addItemSetsTheBackReference() {
        Order order = new Order(CUSTOMER, "USD");
        OrderItem item = new OrderItem("SKU-1", "Widget", 1, new BigDecimal("10.00"));

        order.addItem(item);

        assertThat(order.getItems()).containsExactly(item);
        assertThat(item.getOrder()).isSameAs(order);
    }

    @Test
    void recalculateTotalSumsQuantityTimesUnitPrice() {
        Order order = new Order(CUSTOMER, "USD");
        order.addItem(new OrderItem("SKU-1", "Keyboard", 1, new BigDecimal("89.90")));
        order.addItem(new OrderItem("SKU-2", "Cable", 2, new BigDecimal("9.99")));

        order.recalculateTotal();

        assertThat(order.getTotalAmount()).isEqualByComparingTo("109.88");
    }

    @Test
    void validTransitionMovesTheStatus() {
        Order order = new Order(CUSTOMER, "USD");

        order.transitionTo(OrderStatus.CONFIRMED);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void illegalTransitionThrowsAndLeavesStatusUntouched() {
        Order order = new Order(CUSTOMER, "USD");

        assertThatThrownBy(() -> order.transitionTo(OrderStatus.SHIPPED))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("NEW")
                .hasMessageContaining("SHIPPED");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.NEW);
    }
}
