package com.rtap.streaming.common.serde;

import com.rtap.streaming.common.model.BusinessEvent;
import com.rtap.streaming.common.model.ConsumedEvent;
import com.rtap.streaming.common.model.DeadLetter;
import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.connector.kafka.source.reader.deserializer.KafkaRecordDeserializationSchema;
import org.apache.flink.metrics.Counter;
import org.apache.flink.util.Collector;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Poison-pill-safe deserializer for {@code events.raw.v1}: every record becomes a
 * {@link ConsumedEvent} — either a valid {@link BusinessEvent} or a {@link DeadLetter}
 * envelope carrying the raw bytes and source coordinates. The stream must keep
 * flowing; errors are data (ARCHITECTURE.md §2.6).
 *
 * <p>Validation happens here at the edge: an event without {@code eventType} or a
 * positive {@code occurredAt} cannot be windowed and is dead-lettered, not guessed at.
 */
public class BusinessEventEnvelopeDeserializer implements KafkaRecordDeserializationSchema<ConsumedEvent> {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(BusinessEventEnvelopeDeserializer.class);

    /**
     * Same contract as the API's metric-key validation: dot-separated lowercase
     * segments. Enforced at the pipeline edge so producer-controlled event types can
     * never smuggle hostile strings into downstream identifiers (ES ids, Kafka keys).
     */
    static final java.util.regex.Pattern EVENT_TYPE_PATTERN =
            java.util.regex.Pattern.compile("^[a-z0-9]+(\\.[a-z0-9_-]+)*$");
    private static final int EVENT_TYPE_MAX_LENGTH = 100;

    private transient Counter deserializeFailures;

    @Override
    public void open(DeserializationSchema.InitializationContext context) throws Exception {
        deserializeFailures = context.getMetricGroup().counter("deserializeFailures");
    }

    @Override
    public void deserialize(ConsumerRecord<byte[], byte[]> record, Collector<ConsumedEvent> out) throws IOException {
        byte[] value = record.value();
        if (value == null || value.length == 0) {
            dead(record, "empty payload", out);
            return;
        }
        BusinessEvent event;
        try {
            event = Json.MAPPER.readValue(value, BusinessEvent.class);
        } catch (IOException e) {
            dead(record, "malformed JSON: " + e.getMessage(), out);
            return;
        }
        if (event.getEventType() == null || event.getEventType().isBlank()) {
            dead(record, "missing eventType", out);
        } else if (event.getEventType().length() > EVENT_TYPE_MAX_LENGTH
                || !EVENT_TYPE_PATTERN.matcher(event.getEventType()).matches()) {
            dead(record, "invalid eventType format", out);
        } else if (event.getOccurredAt() <= 0) {
            dead(record, "missing/invalid occurredAt", out);
        } else {
            out.collect(ConsumedEvent.of(event));
        }
    }

    private void dead(ConsumerRecord<byte[], byte[]> record, String error, Collector<ConsumedEvent> out) {
        if (deserializeFailures != null) {
            deserializeFailures.inc();
        }
        // Never log the payload itself (PII / volume); coordinates are enough to triage.
        LOG.warn("Dead-lettering record {}-{}@{}: {}", record.topic(), record.partition(), record.offset(), error);
        out.collect(ConsumedEvent.deadLetter(
                DeadLetter.of(record.topic(), record.partition(), record.offset(), error, record.value())));
    }

    @Override
    public TypeInformation<ConsumedEvent> getProducedType() {
        return TypeInformation.of(ConsumedEvent.class);
    }
}
