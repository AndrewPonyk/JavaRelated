package com.ehrplatform.common.exception;

/**
 * Thrown when a requested resource (e.g. a FHIR Patient) does not exist.
 * Maps to HTTP 404 / FHIR issue code {@code not-found}.
 */
public class ResourceNotFoundException extends EhrPlatformException {

    public ResourceNotFoundException(String resourceType, String id) {
        super("RESOURCE_NOT_FOUND", "%s/%s not found".formatted(resourceType, id));
    }
}
