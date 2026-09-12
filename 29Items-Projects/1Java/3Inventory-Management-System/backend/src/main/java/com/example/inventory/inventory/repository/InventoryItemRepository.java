package com.example.inventory.inventory.repository;

import com.example.inventory.inventory.domain.InventoryItem;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.query.Param;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, UUID>,
        JpaSpecificationExecutor<InventoryItem> {

    @Override
    @EntityGraph(attributePaths = "warehouse")
    Optional<InventoryItem> findById(UUID id);

    @Override
    @EntityGraph(attributePaths = "warehouse")
    Page<InventoryItem> findAll(Specification<InventoryItem> specification, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from InventoryItem item join fetch item.warehouse where item.id = :id")
    Optional<InventoryItem> lockById(@Param("id") UUID id);

    @EntityGraph(attributePaths = "warehouse")
    Optional<InventoryItem> findByWarehouseIdAndSku(UUID warehouseId, String sku);

    boolean existsByWarehouseIdAndSku(UUID warehouseId, String sku);

    boolean existsByWarehouseIdAndBarcode(UUID warehouseId, String barcode);

    boolean existsByWarehouseIdAndActiveTrue(UUID warehouseId);
}
