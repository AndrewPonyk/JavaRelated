package com.example.inventory.stock.repository;

import com.example.inventory.stock.domain.Reservation;
import com.example.inventory.stock.domain.ReservationStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    @EntityGraph(attributePaths = {"item", "item.warehouse"})
    Page<Reservation> findByItemId(UUID itemId, Pageable pageable);

    boolean existsByExternalReference(String externalReference);

    boolean existsByItemIdAndStatus(UUID itemId, ReservationStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select reservation from Reservation reservation join fetch reservation.item item "
            + "join fetch item.warehouse where reservation.id = :id")
    Optional<Reservation> lockById(@Param("id") UUID id);
}
