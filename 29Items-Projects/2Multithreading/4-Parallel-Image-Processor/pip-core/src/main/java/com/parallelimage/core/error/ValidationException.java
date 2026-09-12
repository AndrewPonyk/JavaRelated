package com.parallelimage.core.error;

import java.util.List;

/**
 * Input rejected before any work was scheduled.
 *
 * <p>Maps to HTTP 400 in the local control API and to a form-level error in the UI. Aggregates
 * <em>all</em> violations rather than failing on the first one — a caller fixing a batch request
 * should not have to submit six times.
 */
public final class ValidationException extends PipException {

    private static final long serialVersionUID = 1L;

    private final List<String> violations;

    public ValidationException(List<String> violations) {
        super("invalid request: " + String.join("; ", violations));
        this.violations = List.copyOf(violations);
    }

    public ValidationException(String violation) {
        this(List.of(violation));
    }

    /** Immutable list of human-readable violation messages. Never empty. */
    public List<String> violations() {
        return violations;
    }
}
