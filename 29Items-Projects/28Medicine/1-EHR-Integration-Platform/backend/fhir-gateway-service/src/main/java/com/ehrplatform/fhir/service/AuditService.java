package com.ehrplatform.fhir.service;

import com.ehrplatform.fhir.domain.PhiAudit;
import com.ehrplatform.fhir.repository.PhiAuditRepository;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records every PHI access to the append-only audit trail (HIPAA accounting of
 * disclosures). Runs in its own transaction so an audit write is never rolled
 * back by a failure in the surrounding business transaction.
 */
@Service
public class AuditService {

    private final PhiAuditRepository auditRepository;

    public AuditService(PhiAuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, String resourceType, String resourceId) {
        PhiAudit audit = new PhiAudit(
                currentActor(),
                action,
                resourceType,
                resourceId,
                "TREATMENT",
                null,
                MDC.get("traceId"));
        auditRepository.save(audit);
    }

    private String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return "system";
        }
        return auth.getName();
    }
}
