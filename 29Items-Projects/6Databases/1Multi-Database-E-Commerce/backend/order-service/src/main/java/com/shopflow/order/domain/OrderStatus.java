package com.shopflow.order.domain;

/** Lifecycle states for an order. Transitions are enforced in the service layer. */
public enum OrderStatus {
    PENDING,
    PAID,
    FULFILLED,
    CANCELLED
}
