package com.rtap.streaming.common.serde;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Shared, thread-safe ObjectMapper for the streaming modules. */
public final class Json {

    /** Unknown fields tolerated — producers may add fields without breaking us. */
    public static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private Json() {
    }
}
