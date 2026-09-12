package com.example.pipeline.infrastructure.config;

/**
 * A setting is missing, unparseable or out of range.
 *
 * <p>Always carries the offending key, because "invalid configuration" without a key
 * is the least useful startup failure there is. {@code PipelineApplication} catches
 * this specifically and exits with code {@code 2} — distinguishable from a runtime
 * failure ({@code 1}) so scripts can tell "I typed the flag wrong" from "the pipeline
 * broke".
 */
public final class ConfigurationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String key;

    /**
     * @param key    the property key at fault, e.g. {@code pipeline.queue.capacity}
     * @param detail what is wrong with it, phrased to follow the key
     */
    public ConfigurationException(String key, String detail) {
        super(key + " " + detail);
        this.key = key;
    }

    /**
     * @param key    the property key at fault
     * @param detail what is wrong with it
     * @param cause  the underlying parse failure
     */
    public ConfigurationException(String key, String detail, Throwable cause) {
        super(key + " " + detail, cause);
        this.key = key;
    }

    /** The property key at fault — useful for tests that assert on the key, not the wording. */
    public String key() {
        return key;
    }
}
