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
 * Search projection + raw-resource store for FHIR {@code Observation}.
 */
@Entity
@Table(name = "OBSERVATION_INDEX", indexes = {
        @Index(name = "IX_OBS_PATIENT", columnList = "PATIENT_ID"),
        @Index(name = "IX_OBS_CODE", columnList = "CODE_SYSTEM,CODE_VALUE")
})
public class ObservationIndex {

    @Id
    @Column(name = "FHIR_ID", length = 64)
    private String fhirId;

    @Column(name = "PATIENT_ID", length = 64)
    private String patientId;

    @Column(name = "CODE_SYSTEM", length = 256)
    private String codeSystem;

    @Column(name = "CODE_VALUE", length = 64)
    private String codeValue;

    @Column(name = "STATUS", length = 32)
    private String status;

    @Column(name = "EFFECTIVE_AT")
    private Instant effectiveAt;

    @Lob
    @Column(name = "RESOURCE_JSON")
    private String resourceJson;

    @Column(name = "LAST_UPDATED", nullable = false)
    private Instant lastUpdated;

    @Version
    @Column(name = "VERSION")
    private Long version;

    protected ObservationIndex() {
        // JPA
    }

    public ObservationIndex(String fhirId) {
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

    public String getCodeSystem() {
        return codeSystem;
    }

    public void setCodeSystem(String codeSystem) {
        this.codeSystem = codeSystem;
    }

    public String getCodeValue() {
        return codeValue;
    }

    public void setCodeValue(String codeValue) {
        this.codeValue = codeValue;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getEffectiveAt() {
        return effectiveAt;
    }

    public void setEffectiveAt(Instant effectiveAt) {
        this.effectiveAt = effectiveAt;
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
