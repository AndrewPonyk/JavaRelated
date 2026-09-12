package com.parallelimage.core.error;

import java.nio.file.Path;

/**
 * A single image could not be read or written.
 *
 * <p>This is the canonical <em>per-job</em> failure: it must be converted into a
 * {@link com.parallelimage.core.model.JobOutcome.Failure} by the batch leaf so the remaining
 * 9 999 images in the batch still run.
 */
public final class ImageIoException extends PipException {

    private static final long serialVersionUID = 1L;

    private final transient Path path;

    public ImageIoException(Path path, String message, Throwable cause) {
        super(message + " [" + path + "]", cause);
        this.path = path;
    }

    public ImageIoException(Path path, String message) {
        this(path, message, null);
    }

    /** The offending file, or {@code null} after deserialization. */
    public Path path() {
        return path;
    }
}
