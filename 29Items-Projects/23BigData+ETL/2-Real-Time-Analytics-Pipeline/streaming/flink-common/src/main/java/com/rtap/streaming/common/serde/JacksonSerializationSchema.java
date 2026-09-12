package com.rtap.streaming.common.serde;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.api.common.serialization.SerializationSchema;

/**
 * JSON serializer for sink records. A serialization failure here is a programming
 * error (our own POJOs), so unlike the deserializer it fails loudly.
 */
public class JacksonSerializationSchema<T> implements SerializationSchema<T> {

    private static final long serialVersionUID = 1L;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public byte[] serialize(T element) {
        try {
            return MAPPER.writeValueAsBytes(element);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize " + element.getClass().getSimpleName(), e);
        }
    }
}
