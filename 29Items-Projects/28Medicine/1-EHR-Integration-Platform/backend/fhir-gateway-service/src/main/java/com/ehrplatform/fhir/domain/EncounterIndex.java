package com.ehrplatform.fhir.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

/**
 * Search projection + raw-resource store for FHIR {@code Encounter}.
 */
@Entity
@Table(name = "ENCOUNTER_INDEX", indexes = {
        @Index(name = "IX_ENC_PATIENT", columnList = "PATIENT_ID")
})
public class EncounterIndex {

    @Id
    @Column(name = "FHIR_ID", length = 64)
    private String fhirId;

    @Column(name = "PATIENT_ID", length = 64)
    private String patientId;

    @Column(name = "STATUS", length = 32)
    private String status;

    @Column(name = "CLASS_CODE", length = 32)
    private String classCode;

    @Column(name = "PERIOD_START")
    private Instant periodStart;

    @Column(name = "PERIOD_END")
    private Instant periodEnd;

    @Lob
    @Column(name = "RESOURCE_JSON")
    private String resourceJson;

    @Column(name = "LAST_UPDATED", nullable = false)
    private Instant lastUpdated;

    @Version
    @Column(name = "VERSION")
    private Long version;

    protected EncounterIndex() {
        // JPA
    }

    public EncounterIndex(String fhirId) {
        this.fhirId = fhirId;
    }

    public String getFhirId() {
        return fhirId;
    }

    public String getPatientId() {
        return patientId;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getClassCode() {
        return classCode;
    }

    public void setClassCode(String classCode) {
        this.classCode = classCode;
    }

    public Instant getPeriodStart() {
        return periodStart;
    }

    public void setPeriodStart(Instant periodStart) {
        this.periodStart = periodStart;
    }

    public Instant getPeriodEnd() {
        return periodEnd;
    }

    public void setPeriodEnd(Instant periodEnd) {
        this.periodEnd = periodEnd;
    }

    public String getResourceJson() {
        return resourceJson;
    }

    public void setResourceJson(String resourceJson) {
        this.resourceJson = resourceJson;
    }

    public Instant getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(Instant lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
}
