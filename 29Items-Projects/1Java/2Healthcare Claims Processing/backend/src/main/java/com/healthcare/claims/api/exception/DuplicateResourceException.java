package com.healthcare.claims.api.exception;

/**
 * Exception thrown when attempting to create a resource that already exists.
 */
public class DuplicateResourceException extends BusinessException {

    private final String resourceType;
    private final String identifier;

    public DuplicateResourceException(String resourceType, String identifier) {
        super("DUPLICATE_RESOURCE", String.format("%s already exists: %s", resourceType, identifier));
        this.resourceType = resourceType;
        this.identifier = identifier;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getIdentifier() {
        return identifier;
    }
}
