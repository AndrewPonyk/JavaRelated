package com.parallelimage.core.error;

/**
 * Root of the domain exception hierarchy.
 *
 * <p><strong>Why unchecked:</strong> {@link java.util.concurrent.RecursiveTask#compute()} cannot
 * declare checked exceptions, so anything thrown from inside the fork/join tree must be unchecked.
 * Checked I/O exceptions are wrapped at the boundary (see
 * {@link com.parallelimage.core.io.ImageLoader}) and the original is <em>always</em> preserved as
 * the cause.
 *
 * <p>See {@code docs/ARCHITECTURE.md} §2.6 for the full error-handling philosophy.
 */
public class PipException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PipException(String message) {
        super(message);
    }

    public PipException(String message, Throwable cause) {
        super(message, cause);
    }
}
