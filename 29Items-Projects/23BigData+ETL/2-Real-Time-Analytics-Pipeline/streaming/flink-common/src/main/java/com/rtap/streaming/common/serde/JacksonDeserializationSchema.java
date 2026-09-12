package com.rtap.streaming.common.serde;

import org.apache.flink.api.common.serialization.AbstractDeserializationSchema;
import org.apache.flink.metrics.Counter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Poison-pill-safe JSON deserializer for topics whose producers we own
 * (aggregates, model updates): on failure it logs, counts, and returns {@code null};
 * jobs filter nulls immediately after the source. Raw external input uses
 * {@link BusinessEventEnvelopeDeserializer} instead, which dead-letters the payload.
 */
public class JacksonDeserializationSchema<T> extends AbstractDeserializationSchema<T> {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(JacksonDeserializationSchema.class);

    private final Class<T> type;
    private transient Counter deserializeFailures;

    public JacksonDeserializationSchema(Class<T> type) {
        super(type);
        this.type = type;
    }

    @Override
    public void open(InitializationContext context) throws Exception {
        deserializeFailures = context.getMetricGroup().counter("deserializeFailures");
    }

    @Override
    public T deserialize(byte[] message) throws IOException {
        if (message == null || message.length == 0) {
            return null;
        }
        try {
            return Json.MAPPER.readValue(message, type);
        } catch (IOException e) {
            if (deserializeFailures != null) {
                deserializeFailures.inc();
            }
            // Never log the payload itself at scale (PII / log volume); length triages.
            LOG.warn("Skipping undeserializable {} record ({} bytes): {}",
                    type.getSimpleName(), message.length, e.getMessage());
            return null;
        }
    }
}
