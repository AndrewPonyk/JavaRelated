package com.example.orderservice.repository;

import com.example.orderservice.domain.Order;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    /** Detail fetch: order + items in one SQL round-trip (avoids lazy N+1). */
    @EntityGraph(attributePaths = "items")
    Optional<Order> findWithItemsById(UUID id);

    /** List queries stay single-table; the API returns summaries without items. */
    Page<Order> findByCustomerId(UUID customerId, Pageable pageable);

    /** Idempotency replay lookup — items fetched eagerly so the mapper can run detached. */
    @EntityGraph(attributePaths = "items")
    Optional<Order> findWithItemsByCustomerIdAndIdempotencyKey(UUID customerId, String idempotencyKey);
}
