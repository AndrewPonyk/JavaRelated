package com.ehrplatform.fhir.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Denormalized search index + raw-resource store for FHIR {@code Patient}.
 *
 * <p>The full FHIR resource is persisted as JSON in {@code resourceJson} (Oracle
 * CLOB); the scalar columns are the searchable projection used to satisfy FHIR
 * search params without parsing every CLOB. {@code @Version} gives optimistic
 * locking so concurrent updates surface as 409 conflicts rather than lost writes.
 */
@Entity
@Table(name = "PATIENT_INDEX")
public class PatientIndex {

    @Id
    @Column(name = "FHIR_ID", nullable = false, length = 64)
    private String fhirId;

    @Column(name = "IDENTIFIER_SYSTEM", length = 256)
    private String identifierSystem;

    @Column(name = "IDENTIFIER_VALUE", length = 256)
    private String identifierValue;

    @Column(name = "FAMILY_NAME", length = 256)
    private String familyName;

    @Column(name = "GIVEN_NAME", length = 256)
    private String givenName;

    @Column(name = "BIRTH_DATE")
    private LocalDate birthDate;

    @Lob
    @Column(name = "RESOURCE_JSON")
    private String resourceJson;

    @Column(name = "LAST_UPDATED", nullable = false)
    private Instant lastUpdated;

    @Version
    @Column(name = "VERSION")
    private Long version;

    protected PatientIndex() {
        // for JPA
    }

    public PatientIndex(String fhirId) {
        this.fhirId = fhirId;
    }

    // --- getters / setters (kept terse for the scaffold) ---

    public String getFhirId() {
        return fhirId;
    }

    public String getIdentifierSystem() {
        return identifierSystem;
    }

    public void setIdentifierSystem(String identifierSystem) {
        this.identifierSystem = identifierSystem;
    }

    public String getIdentifierValue() {
        return identifierValue;
    }

    public void setIdentifierValue(String identifierValue) {
        this.identifierValue = identifierValue;
    }

    public String getFamilyName() {
        return familyName;
    }

    public void setFamilyName(String familyName) {
        this.familyName = familyName;
    }

    public String getGivenName() {
        return givenName;
    }

    public void setGivenName(String givenName) {
        this.givenName = givenName;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
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

    public Long getVersion() {
        return version;
    }
}
