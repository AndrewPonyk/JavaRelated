package com.example.inventory.audit;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEntryRepository extends JpaRepository<AuditEntry, UUID> {

    Page<AuditEntry> findByEntityTypeAndEntityIdOrderByOccurredAtDesc(
            String entityType, UUID entityId, Pageable pageable);
}

