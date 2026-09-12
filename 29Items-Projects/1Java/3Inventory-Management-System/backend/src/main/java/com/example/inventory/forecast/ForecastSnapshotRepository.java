package com.example.inventory.forecast;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ForecastSnapshotRepository extends JpaRepository<ForecastSnapshot, UUID> {

    @EntityGraph(attributePaths = {"item", "item.warehouse"})
    Optional<ForecastSnapshot> findFirstByItemIdOrderByGeneratedAtDesc(UUID itemId);
}

