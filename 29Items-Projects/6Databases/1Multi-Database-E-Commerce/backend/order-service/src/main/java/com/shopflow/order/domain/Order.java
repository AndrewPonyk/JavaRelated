package com.shopflow.order.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Order aggregate root (the transactional source of truth, stored in Oracle).
 *
 * <p>The id is a client-opaque UUID string so it is safe to expose externally
 * and stable across systems/events. {@code @Version} provides optimistic
 * locking to guard concurrent status transitions.
 */
@Entity
@Table(name = "ORDERS")
public class Order {

    @Id
    @Column(name = "ID", length = 36)
    private String id;

    @Column(name = "CUSTOMER_ID", nullable = false, length = 64)
    private String customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "CURRENCY", nullable = false, length = 3)
    private String currency;

    @Column(name = "TOTAL_AMOUNT", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount;

    // Pin to plain TIMESTAMP so it matches the Flyway DDL under ddl-auto: validate
    // (values are stored in UTC via hibernate.jdbc.time_zone).
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "IDEMPOTENCY_KEY", length = 64, updatable = false)
    private String idempotencyKey;

    @Version
    @Column(name = "VERSION")
    private long version;

    // Items are intrinsic to the order aggregate and always mapped into the
    // response after the tx closes, so fetch eagerly (batched to avoid N+1)
    // rather than rely on open-session-in-view.
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "ORDER_ID", nullable = false)
    @BatchSize(size = 50)
    private List<OrderItem> items = new ArrayList<>();

    protected Order() {
        // JPA
    }

    private Order(String customerId, String currency, List<OrderItem> items) {
        this.id = UUID.randomUUID().toString();
        this.customerId = customerId;
        this.currency = currency;
        this.items = new ArrayList<>(items);
        this.status = OrderStatus.PENDING;
        this.createdAt = Instant.now();
        this.totalAmount = recomputeTotal();
    }

    /** Factory: build a PENDING order, computing its total from the lines. */
    public static Order create(String customerId, String currency, List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("An order must contain at least one item");
        }
        return new Order(customerId, currency, items);
    }

    private BigDecimal recomputeTotal() {
        return items.stream()
                .map(OrderItem::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Guarded transition to PAID; only valid from PENDING. */
    public void markPaid() {
        if (status != OrderStatus.PENDING) {
            throw new IllegalStateException("Only PENDING orders can be marked PAID; was " + status);
        }
        this.status = OrderStatus.PAID;
    }

    /** Guarded transition to FULFILLED; only valid from PAID. */
    public void fulfill() {
        if (status != OrderStatus.PAID) {
            throw new IllegalStateException("Only PAID orders can be FULFILLED; was " + status);
        }
        this.status = OrderStatus.FULFILLED;
    }

    /** Cancel an order. Allowed from PENDING or PAID; a FULFILLED order cannot be cancelled. */
    public void cancel() {
        if (status == OrderStatus.FULFILLED) {
            throw new IllegalStateException("A FULFILLED order cannot be cancelled");
        }
        if (status == OrderStatus.CANCELLED) {
            return; // idempotent
        }
        this.status = OrderStatus.CANCELLED;
    }

    /** Attach the caller's idempotency key (set once, at creation time). */
    public void assignIdempotencyKey(String key) {
        this.idempotencyKey = key;
    }

    public String getId() {
        return id;
    }

    public String getCustomerId() {
        return customerId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public List<OrderItem> getItems() {
        return List.copyOf(items);
    }
}
