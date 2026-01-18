package com.healthcare.claims.domain.model;

/**
 * Types of healthcare claims that can be processed.
 */
public enum ClaimType {
    MEDICAL("Medical services and procedures"),
    DENTAL("Dental services and procedures"),
    VISION("Vision care services"),
    PHARMACY("Prescription medications"),
    MENTAL_HEALTH("Mental health services"),
    REHABILITATION("Rehabilitation services"),
    DURABLE_MEDICAL_EQUIPMENT("Medical equipment and supplies"),
    LABORATORY("Lab tests and diagnostics"),
    RADIOLOGY("Imaging services"),
    EMERGENCY("Emergency room visits"),
    INPATIENT("Hospital inpatient stays"),
    OUTPATIENT("Outpatient procedures");

    private final String description;

    ClaimType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Checks if this claim type typically requires pre-authorization.
     */
    public boolean requiresPreAuthorization() {
        return switch (this) {
            case INPATIENT, DURABLE_MEDICAL_EQUIPMENT, REHABILITATION -> true;
            default -> false;
        };
    }

    /**
     * Gets the default processing priority for this claim type.
     */
    public int getDefaultPriority() {
        return switch (this) {
            case EMERGENCY -> 1;
            case INPATIENT -> 2;
            case PHARMACY -> 3;
            default -> 5;
        };
    }
}
