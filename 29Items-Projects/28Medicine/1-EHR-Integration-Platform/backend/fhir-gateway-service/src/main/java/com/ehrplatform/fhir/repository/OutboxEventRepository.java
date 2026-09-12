package com.ehrplatform.fhir.repository;

import com.ehrplatform.fhir.domain.OutboxEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Repository for the transactional {@link OutboxEvent} table. */
@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {

    /**
     * Unsent rows, oldest first. No SQL row-limit on purpose: Hibernate 6 emits
     * 12c+ {@code FETCH FIRST} for a {@code Limit}/{@code Pageable}, which Oracle
     * 11.2 rejects (ORA-00933). The relay caps the batch size in Java instead.
     */
    List<OutboxEvent> findBySentAtIsNullOrderByCreatedAtAsc();

    long countBySentAtIsNull();
}
