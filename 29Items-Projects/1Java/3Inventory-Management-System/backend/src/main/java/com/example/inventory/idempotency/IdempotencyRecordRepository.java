package com.example.inventory.idempotency;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, String> {

    @Modifying
    @Transactional
    @Query("delete from IdempotencyRecord record where record.expiresAt < :now")
    int deleteExpired(Instant now);
}

