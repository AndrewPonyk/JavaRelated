package com.example.orderservice.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Order lifecycle state machine.
 *
 * <pre>
 * NEW ──► CONFIRMED ──► PAID ──► SHIPPED ──► DELIVERED
 *  │          │
 *  └──────────┴────► CANCELLED
 * </pre>
 *
 * The transition table is the single source of truth; services must call
 * {@link Order#transitionTo(OrderStatus)} instead of mutating status fields directly.
 */
public enum OrderStatus {

    NEW,
    CONFIRMED,
    PAID,
    SHIPPED,
    DELIVERED,
    CANCELLED;

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = Map.of(
            NEW, EnumSet.of(CONFIRMED, CANCELLED),
            CONFIRMED, EnumSet.of(PAID, CANCELLED),
            PAID, EnumSet.of(SHIPPED),
            SHIPPED, EnumSet.of(DELIVERED),
            DELIVERED, EnumSet.noneOf(OrderStatus.class),
            CANCELLED, EnumSet.noneOf(OrderStatus.class));

    public boolean canTransitionTo(OrderStatus target) {
        return ALLOWED_TRANSITIONS.get(this).contains(target);
    }

    /** Terminal states accept no further transitions. */
    public boolean isTerminal() {
        return ALLOWED_TRANSITIONS.get(this).isEmpty();
    }
}
