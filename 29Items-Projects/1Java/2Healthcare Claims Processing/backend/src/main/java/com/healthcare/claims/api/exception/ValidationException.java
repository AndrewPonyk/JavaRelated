package com.healthcare.claims.api.exception;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Exception thrown when validation fails.
 */
public class ValidationException extends BusinessException {

    private final List<ValidationError> errors;

    public ValidationException(String message) {
        super("VALIDATION_ERROR", message);
        this.errors = new ArrayList<>();
    }

    public ValidationException(String field, String message) {
        super("VALIDATION_ERROR", message);
        this.errors = new ArrayList<>();
        this.errors.add(new ValidationError(field, message));
    }

    public ValidationException(List<ValidationError> errors) {
        super("VALIDATION_ERROR", "Validation failed");
        this.errors = errors != null ? new ArrayList<>(errors) : new ArrayList<>();
    }

    public List<ValidationError> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    public void addError(String field, String message) {
        errors.add(new ValidationError(field, message));
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public record ValidationError(String field, String message) {}
}
