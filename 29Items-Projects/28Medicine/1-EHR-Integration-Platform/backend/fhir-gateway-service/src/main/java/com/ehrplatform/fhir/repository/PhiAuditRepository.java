package com.ehrplatform.fhir.repository;

import com.ehrplatform.fhir.domain.PhiAudit;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Repository for the append-only {@link PhiAudit} trail. */
@Repository
public interface PhiAuditRepository extends JpaRepository<PhiAudit, String> {

    List<PhiAudit> findByResourceTypeAndResourceId(String resourceType, String resourceId);
}
