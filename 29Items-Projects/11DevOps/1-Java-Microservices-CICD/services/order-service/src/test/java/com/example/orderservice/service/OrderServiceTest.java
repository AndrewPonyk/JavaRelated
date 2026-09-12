package com.example.orderservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.orderservice.api.dto.CreateOrderRequest;
import com.example.orderservice.api.dto.OrderItemRequest;
import com.example.orderservice.api.dto.OrderResponse;
import com.example.orderservice.api.dto.OrderSummaryResponse;
import com.example.orderservice.api.dto.PageResponse;
import com.example.orderservice.domain.Order;
import com.example.orderservice.domain.OrderStatus;
import com.example.orderservice.exception.InvalidStatusTransitionException;
import com.example.orderservice.exception.OrderNotFoundException;
import com.example.orderservice.repository.OrderRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final Instant NOW = Instant.parse("2026-02-01T12:00:00Z");
    private static final Instant EARLIER = Instant.parse("2026-01-15T08:30:00Z");
    private static final UUID CUSTOMER = UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7");

    @Mock
    private OrderRepository repository;

    private final OrderMapper mapper = new OrderMapper();

    private OrderService service;

    @BeforeEach
    void setUp() {
        service = new OrderService(repository, mapper, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private CreateOrderRequest sampleRequest() {
        return new CreateOrderRequest(CUSTOMER, "USD", List.of(
                new OrderItemRequest("SKU-1001", "Mechanical Keyboard", 1, new BigDecimal("89.90")),
                new OrderItemRequest("SKU-2002", "USB-C Cable", 2, new BigDecimal("9.99"))));
    }

    /** A persisted-looking aggregate (timestamps set, total computed). */
    private Order existingOrder() {
        Order order = mapper.toEntity(sampleRequest());
        order.recalculateTotal();
        order.setCreatedAt(EARLIER);
        order.setUpdatedAt(EARLIER);
        return order;
    }

    // ── create ─────────────────────────────────────────────────────────────

    @Test
    void createComputesTotalSetsStatusAndTimestamps() {
        when(repository.saveAndFlush(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreationResult result = service.create(sampleRequest(), null);
        OrderResponse response = result.order();

        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        verify(repository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getItems()).hasSize(2);
        assertThat(saved.getValue().getIdempotencyKey()).isNull();

        assertThat(result.replayed()).isFalse();
        assertThat(response.status()).isEqualTo(OrderStatus.NEW);
        assertThat(response.totalAmount()).isEqualByComparingTo("109.88");
        assertThat(response.createdAt()).isEqualTo(NOW);
        assertThat(response.updatedAt()).isEqualTo(NOW);
        assertThat(response.items()).extracting("sku").containsExactly("SKU-1001", "SKU-2002");
    }

    @Test
    void createStoresTheIdempotencyKey() {
        when(repository.findWithItemsByCustomerIdAndIdempotencyKey(CUSTOMER, "key-1")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreationResult result = service.create(sampleRequest(), "key-1");

        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        verify(repository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getIdempotencyKey()).isEqualTo("key-1");
        assertThat(result.replayed()).isFalse();
    }

    @Test
    void createReplaysExistingOrderForKnownIdempotencyKey() {
        Order existing = existingOrder();
        when(repository.findWithItemsByCustomerIdAndIdempotencyKey(CUSTOMER, "key-1")).thenReturn(Optional.of(existing));

        CreationResult result = service.create(sampleRequest(), "key-1");

        assertThat(result.replayed()).isTrue();
        assertThat(result.order().id()).isEqualTo(existing.getId());
        verify(repository, never()).saveAndFlush(any(Order.class));
    }

    @Test
    void createTreatsBlankIdempotencyKeyAsAbsent() {
        when(repository.saveAndFlush(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreationResult result = service.create(sampleRequest(), "   ");

        verify(repository, never()).findWithItemsByCustomerIdAndIdempotencyKey(any(), any());
        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        verify(repository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getIdempotencyKey()).isNull();
        assertThat(result.replayed()).isFalse();
    }

    @Test
    void createLosingTheInsertRaceServesTheWinner() {
        Order winner = existingOrder();
        when(repository.findWithItemsByCustomerIdAndIdempotencyKey(CUSTOMER, "key-1"))
                .thenReturn(Optional.empty(), Optional.of(winner));
        when(repository.saveAndFlush(any(Order.class)))
                .thenThrow(new DataIntegrityViolationException("ux_orders_customer_idempotency"));

        CreationResult result = service.create(sampleRequest(), "key-1");

        assertThat(result.replayed()).isTrue();
        assertThat(result.order().id()).isEqualTo(winner.getId());
    }

    @Test
    void createRethrowsIntegrityViolationsUnrelatedToIdempotency() {
        when(repository.saveAndFlush(any(Order.class)))
                .thenThrow(new DataIntegrityViolationException("ck_orders_total_amount"));

        assertThatThrownBy(() -> service.create(sampleRequest(), null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ── read ───────────────────────────────────────────────────────────────

    @Test
    void getByIdReturnsTheMappedOrder() {
        Order order = existingOrder();
        when(repository.findWithItemsById(order.getId())).thenReturn(Optional.of(order));

        OrderResponse response = service.getById(order.getId());

        assertThat(response.id()).isEqualTo(order.getId());
        assertThat(response.customerId()).isEqualTo(CUSTOMER);
        assertThat(response.items()).hasSize(2);
    }

    @Test
    void getByIdThrowsNotFoundForUnknownId() {
        UUID unknown = UUID.randomUUID();
        when(repository.findWithItemsById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(unknown))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining(unknown.toString());
    }

    @Test
    void listWithoutCustomerFilterUsesFindAll() {
        Order order = existingOrder();
        Pageable pageable = PageRequest.of(0, 20);
        when(repository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(order), pageable, 1));

        PageResponse<OrderSummaryResponse> page = service.list(null, pageable);

        assertThat(page.content()).hasSize(1);
        assertThat(page.content().getFirst().id()).isEqualTo(order.getId());
        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.totalPages()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(20);
    }

    @Test
    void listWithCustomerFilterDelegatesToFindByCustomerId() {
        Order order = existingOrder();
        Pageable pageable = PageRequest.of(0, 10);
        when(repository.findByCustomerId(CUSTOMER, pageable)).thenReturn(new PageImpl<>(List.of(order), pageable, 1));

        PageResponse<OrderSummaryResponse> page = service.list(CUSTOMER, pageable);

        assertThat(page.content()).extracting("customerId").containsExactly(CUSTOMER);
    }

    // ── lifecycle ──────────────────────────────────────────────────────────

    @Test
    void updateStatusAppliesTransitionAndBumpsUpdatedAt() {
        Order order = existingOrder();
        when(repository.findWithItemsById(order.getId())).thenReturn(Optional.of(order));

        OrderResponse response = service.updateStatus(order.getId(), OrderStatus.CONFIRMED);

        assertThat(response.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(response.createdAt()).isEqualTo(EARLIER);
        assertThat(response.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void updateStatusRejectsIllegalTransition() {
        Order order = existingOrder();
        order.transitionTo(OrderStatus.CONFIRMED);
        order.transitionTo(OrderStatus.PAID);
        order.transitionTo(OrderStatus.SHIPPED);
        order.transitionTo(OrderStatus.DELIVERED);
        when(repository.findWithItemsById(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.updateStatus(order.getId(), OrderStatus.CANCELLED))
                .isInstanceOf(InvalidStatusTransitionException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    void updateStatusThrowsNotFoundForUnknownId() {
        UUID unknown = UUID.randomUUID();
        when(repository.findWithItemsById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateStatus(unknown, OrderStatus.CONFIRMED))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void cancelIsAllowedForNewOrders() {
        Order order = existingOrder();
        when(repository.findWithItemsById(order.getId())).thenReturn(Optional.of(order));

        OrderResponse response = service.cancel(order.getId());

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void cancelIsRejectedOnceShipped() {
        Order order = existingOrder();
        order.transitionTo(OrderStatus.CONFIRMED);
        order.transitionTo(OrderStatus.PAID);
        order.transitionTo(OrderStatus.SHIPPED);
        when(repository.findWithItemsById(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.cancel(order.getId()))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }
}
