package com.example.inventory.stock.repository;

import com.example.inventory.stock.domain.ItemBarcode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemBarcodeRepository extends JpaRepository<ItemBarcode, UUID> {

    @EntityGraph(attributePaths = {"item", "item.warehouse"})
    Optional<ItemBarcode> findByBarcodeAndItemWarehouseId(String barcode, UUID warehouseId);

    List<ItemBarcode> findByBarcode(String barcode);

    List<ItemBarcode> findByItemIdOrderByPrimaryDescCreatedAtAsc(UUID itemId);

    boolean existsByItemWarehouseIdAndBarcode(UUID warehouseId, String barcode);

    List<ItemBarcode> findByItemIdIn(List<UUID> itemIds);

    long countByItemId(UUID itemId);
}
