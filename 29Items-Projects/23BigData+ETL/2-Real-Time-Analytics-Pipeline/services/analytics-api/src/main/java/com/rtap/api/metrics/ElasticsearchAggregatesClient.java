package com.rtap.api.metrics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rtap.api.metrics.dto.AggregatePoint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Hot-path aggregate queries against Elasticsearch/OpenSearch.
 *
 * <p>Plain HTTP on the stable {@code _search} API (same reasoning as the Flink sink,
 * ADR #8): a {@code date_histogram fixed_interval} bucket aggregation over the base
 * index merges dimension series and re-buckets to the requested window server-side.
 *
 * <p>Failure posture: an index that doesn't exist yet means "no data" (empty list);
 * anything else throws — {@link MetricsService} falls back to PostgreSQL and logs.
 */
@Component
public class ElasticsearchAggregatesClient {

    private final RestClient rest;
    private final ObjectMapper mapper;
    private final boolean enabled;
    private final Duration hotWindow;

    public ElasticsearchAggregatesClient(RestClient.Builder restBuilder,
                                         ObjectMapper mapper,
                                         @Value("${rtap.elasticsearch.url:http://localhost:9200}") String url,
                                         @Value("${rtap.elasticsearch.enabled:true}") boolean enabled,
                                         @Value("${rtap.elasticsearch.hot-window:PT24H}") Duration hotWindow) {
        this.rest = restBuilder.baseUrl(url).build();
        this.mapper = mapper;
        this.enabled = enabled;
        this.hotWindow = hotWindow;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Ranges ending within this window are eligible for the ES hot path. */
    public Duration hotWindow() {
        return hotWindow;
    }

    public List<AggregatePoint> query(String metricKey, WindowSpec window, Instant from, Instant to) {
        String index = "metrics-aggregates-" + window.baseWindow();
        JsonNode response;
        try {
            response = rest.post()
                    .uri("/{index}/_search", index)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(searchBody(metricKey, window, from, to).toString())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (HttpClientErrorException.NotFound e) {
            return List.of(); // index not created yet — no data, not an error
        }
        if (response == null) {
            throw new IllegalStateException("Empty response from Elasticsearch"); // → PG fallback
        }

        List<AggregatePoint> points = new ArrayList<>();
        for (JsonNode bucket : response.path("aggregations").path("windows").path("buckets")) {
            long count = bucket.path("events").path("value").asLong();
            double sum = bucket.path("total").path("value").asDouble();
            points.add(new AggregatePoint(
                    Instant.ofEpochMilli(bucket.path("key").asLong()),
                    count,
                    sum,
                    bucket.path("low").path("value").asDouble(),
                    bucket.path("high").path("value").asDouble(),
                    count == 0 ? 0.0 : sum / count));
        }
        return points;
    }

    private ObjectNode searchBody(String metricKey, WindowSpec window, Instant from, Instant to) {
        ObjectNode root = mapper.createObjectNode();
        root.put("size", 0);

        var filters = root.putObject("query").putObject("bool").putArray("filter");
        filters.addObject().putObject("term").put("metricKey", metricKey);
        ObjectNode windowStart = filters.addObject().putObject("range").putObject("windowStart");
        windowStart.put("gte", from.toEpochMilli());
        windowStart.put("lt", to.toEpochMilli());

        ObjectNode windows = root.putObject("aggs").putObject("windows");
        ObjectNode histogram = windows.putObject("date_histogram");
        histogram.put("field", "windowStart");
        histogram.put("fixed_interval", window.esInterval());
        histogram.put("min_doc_count", 1);
        ObjectNode subAggs = windows.putObject("aggs");
        subAggs.putObject("events").putObject("sum").put("field", "count");
        subAggs.putObject("total").putObject("sum").put("field", "sum");
        subAggs.putObject("low").putObject("min").put("field", "min");
        subAggs.putObject("high").putObject("max").put("field", "max");
        return root;
    }
}
