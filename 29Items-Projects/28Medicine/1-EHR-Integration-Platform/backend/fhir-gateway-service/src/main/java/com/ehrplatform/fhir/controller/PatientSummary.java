package com.ehrplatform.fhir.controller;

import org.hl7.fhir.r4.model.Patient;

/**
 * Plain, JSON-serializable view of a FHIR {@code Patient} for the non-FHIR
 * {@code /api} surface (HAPI resource objects don't serialize cleanly via
 * Spring's Jackson, so we map to this record).
 */
public record PatientSummary(
        String id,
        String identifierSystem,
        String identifierValue,
        String familyName,
        String givenName,
        String birthDate,
        String gender) {

    /** Map a HAPI {@link Patient} to the summary view. */
    public static PatientSummary from(Patient p) {
        var name = p.getNameFirstRep();
        var identifier = p.getIdentifierFirstRep();
        return new PatientSummary(
                p.getIdElement().getIdPart(),
                identifier.getSystem(),
                identifier.getValue(),
                name.getFamily(),
                name.getGivenAsSingleString(),
                p.hasBirthDate() ? p.getBirthDateElement().getValueAsString() : null,
                p.hasGender() ? p.getGender().toCode() : null);
    }
}
