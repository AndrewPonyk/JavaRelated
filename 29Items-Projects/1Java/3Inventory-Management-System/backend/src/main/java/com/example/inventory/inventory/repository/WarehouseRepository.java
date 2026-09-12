package com.example.inventory.inventory.repository;

import com.example.inventory.inventory.domain.Warehouse;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WarehouseRepository extends JpaRepository<Warehouse, UUID> {

    boolean existsByCode(String code);
}
