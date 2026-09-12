package com.parallelimage.core.error;

/**
 * An {@link com.parallelimage.core.pipeline.ImageOperation} failed while transforming pixels.
 *
 * <p>Carries the operation name so a log line and a UI row can be correlated without a stack trace.
 */
public final class PipelineException extends PipException {

    private static final long serialVersionUID = 1L;

    private final String operationName;

    public PipelineException(String operationName, String message, Throwable cause) {
        super("operation '" + operationName + "' failed: " + message, cause);
        this.operationName = operationName;
    }

    public PipelineException(String operationName, String message) {
        this(operationName, message, null);
    }

    public String operationName() {
        return operationName;
    }
}
