package com.rtap.streaming.aggregation;

import com.fasterxml.jackson.databind.JsonNode;
import com.rtap.streaming.common.model.MetricAggregate;
import com.rtap.streaming.common.serde.Json;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bulk-body construction is a security surface: the document id embeds the
 * producer-controlled metricKey. The envelope deserializer rejects hostile formats
 * upstream; this is the defense-in-depth check that even a hostile key could only
 * ever be DATA in the action line, never structure.
 */
class ElasticsearchBulkSinkTest {

    @Test
    void bulkBodyIsValidNdjsonWithActionThenDocument() throws Exception {
        MetricAggregate agg = aggregate("orders.completed");

        String body = ElasticsearchBulkSink.buildBulkBody(List.of(agg));
        String[] lines = body.split("\n");

        assertThat(lines).hasSize(2);
        JsonNode action = Json.MAPPER.readTree(lines[0]);
        assertThat(action.path("index").path("_index").asText()).isEqualTo("metrics-aggregates-1s");
        assertThat(action.path("index").path("_id").asText()).isEqualTo(agg.documentId());
        JsonNode document = Json.MAPPER.readTree(lines[1]);
        assertThat(document.path("metricKey").asText()).isEqualTo("orders.completed");
        assertThat(document.path("avg").asDouble()).isEqualTo(10.0);
    }

    @Test
    void hostileMetricKeyCannotInjectBulkActionStructure() throws Exception {
        // JSON-injection shaped key: would redirect _index if the action line were concatenated
        MetricAggregate agg = aggregate("x\",\"_index\":\"evil-index\",\"x\":\"");

        String body = ElasticsearchBulkSink.buildBulkBody(List.of(agg));
        String[] lines = body.split("\n");

        JsonNode action = Json.MAPPER.readTree(lines[0]); // parses — quotes were escaped
        assertThat(action.path("index").path("_index").asText())
                .isEqualTo("metrics-aggregates-1s");       // index untouched
        assertThat(action.path("index").path("_id").asText())
                .contains("evil-index");                    // hostile key is inert data in the id
        assertThat(action.path("index").has("x")).isFalse();
    }

    private static MetricAggregate aggregate(String metricKey) {
        MetricAggregate agg = new MetricAggregate();
        agg.setMetricKey(metricKey);
        agg.setWindowSize("1s");
        agg.setWindowStart(1_700_000_000_000L);
        agg.setWindowEnd(1_700_000_001_000L);
        agg.setCount(2);
        agg.setSum(20.0);
        agg.setMin(5.0);
        agg.setMax(15.0);
        agg.setDimensions(Map.of("region", "eu"));
        return agg;
    }
}
