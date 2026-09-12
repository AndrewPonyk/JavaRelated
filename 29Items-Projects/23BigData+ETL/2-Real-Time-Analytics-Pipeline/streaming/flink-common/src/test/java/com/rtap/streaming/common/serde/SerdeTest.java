package com.rtap.streaming.common.serde;

import com.rtap.streaming.common.model.BusinessEvent;
import com.rtap.streaming.common.model.ConsumedEvent;
import com.rtap.streaming.common.model.MetricAggregate;
import org.apache.flink.api.common.functions.util.ListCollector;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SerdeTest {

    private final JacksonSerializationSchema<BusinessEvent> serializer = new JacksonSerializationSchema<>();
    private final JacksonDeserializationSchema<BusinessEvent> deserializer =
            new JacksonDeserializationSchema<>(BusinessEvent.class);

    private static BusinessEvent event() {
        BusinessEvent e = new BusinessEvent();
        e.setEventId("e-1");
        e.setEventType("orders.completed");
        e.setSource("checkout-service");
        e.setOccurredAt(1_700_000_000_123L);
        e.setValue(42.5);
        e.setDimensions(Map.of("region", "eu", "channel", "web"));
        e.setSchemaVersion(1);
        return e;
    }

    @Test
    void roundTripsBusinessEvents() throws IOException {
        BusinessEvent original = event();
        BusinessEvent decoded = deserializer.deserialize(serializer.serialize(original));

        assertThat(decoded).isNotNull();
        assertThat(decoded.getEventId()).isEqualTo("e-1");
        assertThat(decoded.getEventType()).isEqualTo("orders.completed");
        assertThat(decoded.getOccurredAt()).isEqualTo(1_700_000_000_123L);
        assertThat(decoded.getValue()).isEqualTo(42.5);
        assertThat(decoded.getDimensions()).containsEntry("region", "eu");
    }

    @Test
    void toleratesUnknownFieldsFromNewerProducers() throws IOException {
        byte[] payload = """
                {"eventId":"e-2","eventType":"orders.completed","occurredAt":1700000000000,
                 "value":1.0,"newFieldFromV2":"ignored"}
                """.getBytes(StandardCharsets.UTF_8);

        BusinessEvent decoded = deserializer.deserialize(payload);

        assertThat(decoded).isNotNull();
        assertThat(decoded.getEventId()).isEqualTo("e-2");
    }

    @Test
    void returnsNullInsteadOfThrowingOnGarbage() throws IOException {
        assertThat(deserializer.deserialize("not json at all".getBytes(StandardCharsets.UTF_8))).isNull();
        assertThat(deserializer.deserialize(new byte[0])).isNull();
        assertThat(deserializer.deserialize(null)).isNull();
    }

    @Test
    void aggregateJsonIncludesDerivedAvgButNoInternals() throws IOException {
        MetricAggregate agg = new MetricAggregate();
        agg.setMetricKey("orders.completed");
        agg.setWindowSize("1s");
        agg.setWindowStart(1_700_000_000_000L);
        agg.setWindowEnd(1_700_000_001_000L);
        agg.setCount(4);
        agg.setSum(20.0);

        String json = Json.MAPPER.writeValueAsString(agg);

        assertThat(json).contains("\"avg\":5.0");
        assertThat(json).doesNotContain("documentId"); // methods without get-prefix stay internal
    }

    // ── Envelope deserializer: nothing is dropped, poison pills become dead letters ──

    private List<ConsumedEvent> deserializeRecord(byte[] value) throws IOException {
        BusinessEventEnvelopeDeserializer schema = new BusinessEventEnvelopeDeserializer();
        List<ConsumedEvent> out = new ArrayList<>();
        schema.deserialize(new ConsumerRecord<>("events.raw.v1", 3, 42L, null, value),
                new ListCollector<>(out));
        return out;
    }

    @Test
    void envelopePassesValidEventsThrough() throws IOException {
        List<ConsumedEvent> out = deserializeRecord(new JacksonSerializationSchema<BusinessEvent>().serialize(event()));

        assertThat(out).hasSize(1);
        assertThat(out.get(0).isDeadLetter()).isFalse();
        assertThat(out.get(0).getEvent().getEventType()).isEqualTo("orders.completed");
    }

    @Test
    void envelopeDeadLettersMalformedJsonWithReplayablePayload() throws IOException {
        byte[] garbage = "{{{definitely-not-json".getBytes(StandardCharsets.UTF_8);

        List<ConsumedEvent> out = deserializeRecord(garbage);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).isDeadLetter()).isTrue();
        assertThat(out.get(0).getDeadLetter().getError()).contains("malformed JSON");
        assertThat(out.get(0).getDeadLetter().getOriginalTopic()).isEqualTo("events.raw.v1");
        assertThat(out.get(0).getDeadLetter().getPartition()).isEqualTo(3);
        assertThat(out.get(0).getDeadLetter().getOffset()).isEqualTo(42L);
        assertThat(out.get(0).getDeadLetter().decodePayload()).isEqualTo(garbage); // replayable
    }

    @Test
    void envelopeDeadLettersEventsFailingValidation() throws IOException {
        List<ConsumedEvent> missingType =
                deserializeRecord("{\"eventId\":\"x\",\"occurredAt\":1700000000000,\"value\":1}".getBytes(StandardCharsets.UTF_8));
        List<ConsumedEvent> missingTime =
                deserializeRecord("{\"eventId\":\"x\",\"eventType\":\"a.b\",\"value\":1}".getBytes(StandardCharsets.UTF_8));
        List<ConsumedEvent> empty = deserializeRecord(new byte[0]);

        assertThat(missingType.get(0).isDeadLetter()).isTrue();
        assertThat(missingType.get(0).getDeadLetter().getError()).contains("missing eventType");
        assertThat(missingTime.get(0).isDeadLetter()).isTrue();
        assertThat(missingTime.get(0).getDeadLetter().getError()).contains("occurredAt");
        assertThat(empty.get(0).isDeadLetter()).isTrue();
    }

    /** Producer-controlled event types become downstream identifiers — format is enforced at the edge. */
    @Test
    void envelopeDeadLettersHostileEventTypeFormats() throws IOException {
        String[] hostile = {
                "Orders.Completed",                       // uppercase
                "orders completed",                       // whitespace
                "orders\\\",\\\"_index\\\":\\\"evil",     // JSON-injection shaped
                "a".repeat(101),                          // over max length
        };
        for (String eventType : hostile) {
            String json = "{\"eventId\":\"x\",\"eventType\":" + Json.MAPPER.writeValueAsString(eventType)
                    + ",\"occurredAt\":1700000000000,\"value\":1}";
            List<ConsumedEvent> out = deserializeRecord(json.getBytes(StandardCharsets.UTF_8));
            assertThat(out.get(0).isDeadLetter())
                    .as("eventType '%s' must be dead-lettered", eventType)
                    .isTrue();
            assertThat(out.get(0).getDeadLetter().getError()).contains("invalid eventType format");
        }
    }
}
