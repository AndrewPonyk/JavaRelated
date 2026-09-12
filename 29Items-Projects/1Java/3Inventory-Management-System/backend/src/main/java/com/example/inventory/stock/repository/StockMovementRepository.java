package com.example.inventory.stock.repository;

import com.example.inventory.stock.domain.StockMovement;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {

    Page<StockMovement> findByItemIdOrderByOccurredAtDesc(UUID itemId, Pageable pageable);

    @Query("""
            select m from StockMovement m
            where m.item.id = :itemId and m.occurredAt >= :since
            order by m.occurredAt asc
            """)
    List<StockMovement> findDemandHistory(@Param("itemId") UUID itemId,
                                          @Param("since") Instant since);

    @Query("select m.item.id as itemId, sum(m.quantityDelta) as quantity "
            + "from StockMovement m where m.item.id in :itemIds group by m.item.id")
    List<ItemQuantityTotal> sumQuantityDeltaByItemIds(@Param("itemIds") List<UUID> itemIds);

    interface ItemQuantityTotal {
        UUID getItemId();

        long getQuantity();
    }
}
