package com.ehrplatform.common.exception;

/**
 * Base type for all domain exceptions across the platform.
 *
 * <p>Carrying a stable {@code errorCode} lets each protocol boundary
 * (FHIR {@code OperationOutcome}, HL7 {@code NACK}, REST {@code ApiError})
 * map failures consistently without leaking PHI.
 */
public abstract class EhrPlatformException extends RuntimeException {

    private final String errorCode;

    protected EhrPlatformException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    protected EhrPlatformException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
