package com.shopflow.order.service;

import com.shopflow.common.error.ApiException;
import com.shopflow.common.event.OrderPlacedEvent;
import com.shopflow.order.api.dto.CreateOrderRequest;
import com.shopflow.order.domain.Order;
import com.shopflow.order.domain.OrderItem;
import com.shopflow.order.messaging.OrderEventPublisher;
import com.shopflow.order.repository.OrderRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Application service for orders. Owns the transactional boundary: persistence
 * to Oracle and emission of the {@code OrderPlacedEvent} happen as one logical
 * unit, with the event published only <em>after</em> the DB commit succeeds.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final OrderEventPublisher eventPublisher;

    public OrderService(OrderRepository orderRepository, OrderEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Create and persist a new order, then publish its domain event after commit.
     *
     * @param request validated order request
     * @return the persisted order
     */
    /** Convenience overload for unkeyed creation. */
    @Transactional
    public Order createOrder(CreateOrderRequest request) {
        return createOrder(request, null);
    }

    /**
     * Create and persist a new order, then publish its domain event after commit.
     * If {@code idempotencyKey} is supplied and matches a prior request, the
     * existing order is returned instead of creating a duplicate (no double-charge).
     */
    @Transactional
    public Order createOrder(CreateOrderRequest request, String idempotencyKey) {
        boolean keyed = idempotencyKey != null && !idempotencyKey.isBlank();
        if (keyed) {
            Optional<Order> existing = orderRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                log.info("Idempotent replay for key {} -> order {}", idempotencyKey, existing.get().getId());
                return existing.get();
            }
        }

        List<OrderItem> items = request.items().stream()
                .map(i -> new OrderItem(i.productId(), i.quantity(), i.unitPrice()))
                .toList();

        Order order = Order.create(request.customerId(), request.currency(), items);
        if (keyed) {
            order.assignIdempotencyKey(idempotencyKey);
        }
        Order saved = orderRepository.save(order);
        log.info("Created order {} for customer {} total {} {}",
                saved.getId(), saved.getCustomerId(), saved.getTotalAmount(), saved.getCurrency());

        publishAfterCommit(toEvent(saved));
        return saved;
    }

    @Transactional(readOnly = true)
    public Order getOrder(String id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Order", id));
    }

    @Transactional(readOnly = true)
    public Page<Order> listForCustomer(String customerId, Pageable pageable) {
        return orderRepository.findByCustomerIdOrderByCreatedAtDesc(customerId, pageable);
    }

    @Transactional
    public Order markPaid(String id) {
        Order order = getOrder(id);
        order.markPaid(); // guarded transition; throws IllegalStateException on bad state
        return order;      // dirty-checked, flushed on commit
    }

    @Transactional
    public Order fulfill(String id) {
        Order order = getOrder(id);
        order.fulfill();
        return order;
    }

    @Transactional
    public Order cancel(String id) {
        Order order = getOrder(id);
        order.cancel();
        return order;
    }

    // --- helpers -------------------------------------------------------------

    private OrderPlacedEvent toEvent(Order order) {
        List<OrderPlacedEvent.Line> lines = order.getItems().stream()
                .map(i -> new OrderPlacedEvent.Line(i.getProductId(), i.getQuantity(), i.getUnitPrice()))
                .toList();
        return new OrderPlacedEvent(
                UUID.randomUUID(),
                MDC.get("traceId"),
                1,
                Instant.now(),
                order.getId(),
                order.getCustomerId(),
                order.getTotalAmount(),
                order.getCurrency(),
                lines);
    }

    /**
     * Defer event publication until the surrounding transaction commits, so we
     * never advertise an order that was rolled back. If there is no active
     * transaction (e.g. unit test), publish immediately.
     */
    private void publishAfterCommit(OrderPlacedEvent event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    eventPublisher.publish(event);
                }
            });
        } else {
            eventPublisher.publish(event);
        }
    }
}
