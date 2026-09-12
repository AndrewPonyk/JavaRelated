package com.ehrplatform.common.exception;

/**
 * Thrown when input fails validation (FHIR profile, business rule, or HL7 segment).
 * Maps to HTTP 422 / FHIR issue code {@code invalid}.
 */
public class ValidationException extends EhrPlatformException {

    public ValidationException(String message) {
        super("VALIDATION_FAILED", message);
    }
}
