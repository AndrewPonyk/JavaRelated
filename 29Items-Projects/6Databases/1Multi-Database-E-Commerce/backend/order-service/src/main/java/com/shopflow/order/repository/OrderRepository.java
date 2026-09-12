package com.shopflow.order.repository;

import com.shopflow.order.domain.Order;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link Order}. Backed by Oracle; query methods
 * derive SQL automatically. The aggregate is loaded with its items via the
 * configured fetch plan.
 */
public interface OrderRepository extends JpaRepository<Order, String> {

    /** Orders for a customer, newest first, paginated. */
    Page<Order> findByCustomerIdOrderByCreatedAtDesc(String customerId, Pageable pageable);

    /** Convenience lookup used by reporting/tests. */
    List<Order> findByCustomerId(String customerId);

    /** Idempotency lookup: returns a prior order created with the same key, if any. */
    Optional<Order> findByIdempotencyKey(String idempotencyKey);
}
