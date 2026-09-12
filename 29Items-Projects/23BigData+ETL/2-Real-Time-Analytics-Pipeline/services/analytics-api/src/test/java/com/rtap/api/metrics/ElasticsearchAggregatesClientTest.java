package com.rtap.api.metrics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rtap.api.metrics.dto.AggregatePoint;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ElasticsearchAggregatesClientTest {

    private static final String BUCKETS_RESPONSE = """
            {"aggregations":{"windows":{"buckets":[
              {"key":1700000000000,"doc_count":2,
               "events":{"value":5},"total":{"value":50.0},"low":{"value":2.0},"high":{"value":20.0}},
              {"key":1700000060000,"doc_count":1,
               "events":{"value":2},"total":{"value":30.0},"low":{"value":10.0},"high":{"value":20.0}}
            ]}}}
            """;

    private ElasticsearchAggregatesClient client(MockRestServiceServer[] serverHolder) {
        RestClient.Builder builder = RestClient.builder();
        serverHolder[0] = MockRestServiceServer.bindTo(builder).build();
        return new ElasticsearchAggregatesClient(builder, new ObjectMapper(),
                "", true, Duration.ofHours(24));
    }

    @Test
    void parsesDateHistogramBucketsIntoPoints() {
        MockRestServiceServer[] holder = new MockRestServiceServer[1];
        ElasticsearchAggregatesClient client = client(holder);
        holder[0].expect(requestTo("/metrics-aggregates-1m/_search"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(jsonPath("$.query.bool.filter[0].term.metricKey").value("orders.completed"))
                .andExpect(jsonPath("$.aggs.windows.date_histogram.fixed_interval").value("1m"))
                .andRespond(withSuccess(BUCKETS_RESPONSE, MediaType.APPLICATION_JSON));

        List<AggregatePoint> points = client.query("orders.completed", WindowSpec.ONE_MINUTE,
                Instant.ofEpochMilli(1_700_000_000_000L), Instant.ofEpochMilli(1_700_000_120_000L));

        assertThat(points).hasSize(2);
        assertThat(points.get(0).windowStart()).isEqualTo(Instant.ofEpochMilli(1_700_000_000_000L));
        assertThat(points.get(0).count()).isEqualTo(5);
        assertThat(points.get(0).sum()).isEqualTo(50.0);
        assertThat(points.get(0).min()).isEqualTo(2.0);
        assertThat(points.get(0).max()).isEqualTo(20.0);
        assertThat(points.get(0).avg()).isEqualTo(10.0);
    }

    @Test
    void coarserWindowsQueryTheBaseResolutionIndex() {
        MockRestServiceServer[] holder = new MockRestServiceServer[1];
        ElasticsearchAggregatesClient client = client(holder);
        // 10s window re-buckets the 1s base index server-side
        holder[0].expect(requestTo("/metrics-aggregates-1s/_search"))
                .andExpect(jsonPath("$.aggs.windows.date_histogram.fixed_interval").value("10s"))
                .andRespond(withSuccess("{\"aggregations\":{\"windows\":{\"buckets\":[]}}}",
                        MediaType.APPLICATION_JSON));

        List<AggregatePoint> points = client.query("orders.completed", WindowSpec.TEN_SECONDS,
                Instant.EPOCH, Instant.ofEpochMilli(60_000));

        assertThat(points).isEmpty();
    }

    @Test
    void missingIndexMeansNoDataNotAnError() {
        MockRestServiceServer[] holder = new MockRestServiceServer[1];
        ElasticsearchAggregatesClient client = client(holder);
        holder[0].expect(requestTo("/metrics-aggregates-1m/_search"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        List<AggregatePoint> points = client.query("orders.completed", WindowSpec.ONE_MINUTE,
                Instant.EPOCH, Instant.ofEpochMilli(60_000));

        assertThat(points).isEmpty();
    }
}
