package com.example.inventory.alert;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LowStockAlertRepository extends JpaRepository<LowStockAlert, UUID> {

    @EntityGraph(attributePaths = {"item", "item.warehouse"})
    Page<LowStockAlert> findByStatusOrderByCreatedAtDesc(AlertStatus status, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"item", "item.warehouse"})
    Optional<LowStockAlert> findById(UUID id);

    boolean existsByEventId(UUID eventId);

    Optional<LowStockAlert> findFirstByItemIdAndStatusOrderByCreatedAtDesc(UUID itemId, AlertStatus status);
}
