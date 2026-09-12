package com.ehrplatform.fhir.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Append-only PHI access record (HIPAA accounting of disclosures).
 * Stores references (type + id) and context — never names or clinical content.
 */
@Entity
@Table(name = "PHI_AUDIT")
public class PhiAudit {

    @Id
    @Column(name = "AUDIT_ID", length = 64)
    private String auditId;

    @Column(name = "ACTOR_ID", nullable = false, length = 256)
    private String actorId;

    @Column(name = "ACTION", nullable = false, length = 32)
    private String action;

    @Column(name = "RESOURCE_TYPE", nullable = false, length = 64)
    private String resourceType;

    @Column(name = "RESOURCE_ID", length = 64)
    private String resourceId;

    @Column(name = "PURPOSE_OF_USE", length = 64)
    private String purposeOfUse;

    @Column(name = "SOURCE_IP", length = 64)
    private String sourceIp;

    @Column(name = "OCCURRED_AT", nullable = false)
    private Instant occurredAt;

    @Column(name = "TRACE_ID", length = 64)
    private String traceId;

    protected PhiAudit() {
        // JPA
    }

    public PhiAudit(String actorId, String action, String resourceType, String resourceId,
                    String purposeOfUse, String sourceIp, String traceId) {
        this.auditId = UUID.randomUUID().toString();
        this.actorId = actorId;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.purposeOfUse = purposeOfUse;
        this.sourceIp = sourceIp;
        this.traceId = traceId;
        this.occurredAt = Instant.now();
    }

    public String getAuditId() {
        return auditId;
    }

    public String getActorId() {
        return actorId;
    }

    public String getAction() {
        return action;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
