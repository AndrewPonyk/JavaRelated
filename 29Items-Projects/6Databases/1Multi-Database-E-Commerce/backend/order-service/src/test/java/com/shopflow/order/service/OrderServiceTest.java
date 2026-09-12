package com.shopflow.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shopflow.common.error.ApiException;
import com.shopflow.common.event.OrderPlacedEvent;
import com.shopflow.order.api.dto.CreateOrderRequest;
import com.shopflow.order.domain.Order;
import com.shopflow.order.domain.OrderStatus;
import com.shopflow.order.messaging.OrderEventPublisher;
import com.shopflow.order.repository.OrderRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link OrderService}. Persistence and Kafka are mocked, so
 * these run in milliseconds and assert pure business behaviour. Repository and
 * event round-trips are covered separately by Testcontainers integration tests.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderEventPublisher eventPublisher;

    @InjectMocks
    private OrderService orderService;

    @Test
    @DisplayName("createOrder computes the total from lines and publishes an event")
    void createOrder_computesTotal_andPublishes() {
        CreateOrderRequest request = new CreateOrderRequest("cust-1", "EUR", List.of(
                new CreateOrderRequest.Item("prod-A", 2, new BigDecimal("10.00")),
                new CreateOrderRequest.Item("prod-B", 1, new BigDecimal("5.50"))));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order result = orderService.createOrder(request);

        // 2 * 10.00 + 1 * 5.50 = 25.50
        assertThat(result.getTotalAmount()).isEqualByComparingTo("25.50");
        assertThat(result.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(result.getItems()).hasSize(2);

        // No active transaction in a unit test → event publishes immediately.
        ArgumentCaptor<OrderPlacedEvent> captor = ArgumentCaptor.forClass(OrderPlacedEvent.class);
        verify(eventPublisher).publish(captor.capture());
        assertThat(captor.getValue().customerId()).isEqualTo("cust-1");
        assertThat(captor.getValue().totalAmount()).isEqualByComparingTo("25.50");
    }

    @Test
    @DisplayName("getOrder throws 404 ApiException when the order is absent")
    void getOrder_missing_throwsNotFound() {
        when(orderRepository.findById("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrder("nope"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("markPaid rejects an illegal transition from a non-PENDING order")
    void markPaid_fromPaid_isRejected() {
        Order order = Order.create("cust-1", "EUR",
                List.of(new com.shopflow.order.domain.OrderItem("p", 1, new BigDecimal("9.99"))));
        order.markPaid(); // now PAID
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.markPaid(order.getId()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("full lifecycle PENDING -> PAID -> FULFILLED is allowed")
    void lifecycle_paidThenFulfilled() {
        Order order = Order.create("cust-1", "EUR",
                List.of(new com.shopflow.order.domain.OrderItem("p", 1, new BigDecimal("9.99"))));
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        orderService.markPaid(order.getId());
        Order fulfilled = orderService.fulfill(order.getId());

        assertThat(fulfilled.getStatus()).isEqualTo(OrderStatus.FULFILLED);
    }

    @Test
    @DisplayName("cancel moves a PENDING order to CANCELLED")
    void cancel_pendingOrder() {
        Order order = Order.create("cust-1", "EUR",
                List.of(new com.shopflow.order.domain.OrderItem("p", 1, new BigDecimal("9.99"))));
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        assertThat(orderService.cancel(order.getId()).getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("createOrder with a known idempotency key returns the existing order without re-saving")
    void createOrder_idempotentReplay_returnsExisting() {
        Order existing = Order.create("cust-1", "EUR",
                List.of(new com.shopflow.order.domain.OrderItem("p", 1, new BigDecimal("9.99"))));
        when(orderRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existing));

        CreateOrderRequest request = new CreateOrderRequest("cust-1", "EUR",
                List.of(new CreateOrderRequest.Item("p", 1, new BigDecimal("9.99"))));
        Order result = orderService.createOrder(request, "key-1");

        assertThat(result).isSameAs(existing);
        verify(orderRepository, never()).save(any(Order.class));
        verify(eventPublisher, never()).publish(any());
    }
}
