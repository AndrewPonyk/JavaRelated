package com.ehrplatform.fhir.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;

/**
 * Validated request body for the non-FHIR admin registration endpoint.
 *
 * <p>Demonstrates the bean-validation pattern: constraints are declared
 * declaratively and enforced by {@code @Valid} at the controller boundary, so
 * invalid input is rejected before it reaches the service layer.
 *
 * @param identifierSystem MRN system URI (e.g. {@code urn:oid:...})
 * @param identifierValue  medical record number
 * @param familyName       patient family (last) name — required
 * @param givenName        patient given (first) name — required
 * @param birthDate        date of birth — must be in the past
 * @param gender           administrative gender (FHIR value set)
 */
public record PatientRegistrationRequest(
        @NotBlank String identifierSystem,
        @NotBlank String identifierValue,
        @NotBlank String familyName,
        @NotBlank String givenName,
        @Past LocalDate birthDate,
        @Pattern(regexp = "male|female|other|unknown",
                message = "gender must be one of: male, female, other, unknown")
        String gender) {
}
