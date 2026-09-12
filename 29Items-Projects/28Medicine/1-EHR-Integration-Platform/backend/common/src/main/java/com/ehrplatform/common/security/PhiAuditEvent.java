package com.ehrplatform.common.security;

import java.time.Instant;

/**
 * Immutable HIPAA audit record for any access to PHI.
 *
 * <p>Emitted on every read/write of protected health information for the HIPAA
 * "accounting of disclosures" requirement. Deliberately records <em>references</em>
 * (resource type + id) and the actor's purpose — never names, identifiers, or
 * clinical content.
 *
 * @param actorId       authenticated principal (user or system account)
 * @param action        READ | CREATE | UPDATE | DELETE | SEARCH | EXPORT
 * @param resourceType  FHIR resource type touched
 * @param resourceId    logical id touched
 * @param purposeOfUse  e.g. TREATMENT, PAYMENT, OPERATIONS
 * @param sourceIp      caller IP (for forensics)
 * @param occurredAt    server timestamp
 * @param traceId       correlation id
 */
public record PhiAuditEvent(
        String actorId,
        String action,
        String resourceType,
        String resourceId,
        String purposeOfUse,
        String sourceIp,
        Instant occurredAt,
        String traceId) {

    // The FHIR gateway persists audit records via its PhiAudit JPA entity +
    // AuditService (append-only, retained >= 6 years). This record is the
    // cross-service DTO form for shipping audit events to a SIEM.
}
