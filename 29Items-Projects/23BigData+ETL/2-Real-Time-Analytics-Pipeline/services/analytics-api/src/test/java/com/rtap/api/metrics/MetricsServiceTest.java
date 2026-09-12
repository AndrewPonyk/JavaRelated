package com.rtap.api.metrics;

import com.rtap.api.metrics.dto.AggregatePoint;
import com.rtap.api.metrics.dto.CreateMetricRequest;
import com.rtap.api.metrics.dto.MetricDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetricsServiceTest {

    private static final MetricDefinition ORDERS =
            new MetricDefinition("orders.completed", "Orders completed", "count", null, Instant.EPOCH);
    private static final AggregatePoint POINT =
            new AggregatePoint(Instant.now(), 3, 30.0, 5.0, 15.0, 10.0);

    @Mock MetricDefinitionRepository definitions;
    @Mock MetricAggregateRepository aggregates;
    @Mock ElasticsearchAggregatesClient elasticsearch;

    private MetricsService service;

    @BeforeEach
    void setUp() {
        service = new MetricsService(definitions, aggregates, elasticsearch);
        lenient().when(definitions.find("orders.completed")).thenReturn(Optional.of(ORDERS));
        lenient().when(elasticsearch.hotWindow()).thenReturn(Duration.ofHours(24));
    }

    @Test
    void unknownMetricIs404() {
        when(definitions.find("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.queryAggregates("nope", "1m", null, null))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void invertedRangeIsRejected() {
        Instant now = Instant.now();

        assertThatThrownBy(() -> service.queryAggregates("orders.completed", "1m", now, now.minusSeconds(60)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'from' must be before 'to'");
    }

    @Test
    void oversizedPointCountIsRejectedWithGuidance() {
        Instant now = Instant.now();

        // 2h at 1s resolution = 7200 points > 5000
        assertThatThrownBy(() -> service.queryAggregates("orders.completed", "1s", now.minus(Duration.ofHours(2)), now))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("coarser window");
    }

    @Test
    void hotRangePrefersElasticsearch() {
        when(elasticsearch.isEnabled()).thenReturn(true);
        when(elasticsearch.query(anyString(), any(), any(), any())).thenReturn(List.of(POINT));

        List<AggregatePoint> result = service.queryAggregates("orders.completed", "1m", null, null);

        assertThat(result).containsExactly(POINT);
        verify(aggregates, never()).query(anyString(), any(), any(), any());
    }

    @Test
    void elasticsearchFailureFallsBackToPostgres() {
        when(elasticsearch.isEnabled()).thenReturn(true);
        when(elasticsearch.query(anyString(), any(), any(), any())).thenThrow(new RuntimeException("boom"));
        when(aggregates.query(anyString(), any(), any(), any())).thenReturn(List.of(POINT));

        List<AggregatePoint> result = service.queryAggregates("orders.completed", "1m", null, null);

        assertThat(result).containsExactly(POINT);
    }

    @Test
    void historicalRangeGoesStraightToPostgres() {
        when(elasticsearch.isEnabled()).thenReturn(true);
        when(aggregates.query(anyString(), any(), any(), any())).thenReturn(List.of(POINT));
        Instant weekAgo = Instant.now().minus(Duration.ofDays(7));

        List<AggregatePoint> result = service.queryAggregates(
                "orders.completed", "1h", weekAgo.minus(Duration.ofDays(1)), weekAgo);

        assertThat(result).containsExactly(POINT);
        verify(elasticsearch, never()).query(anyString(), any(), any(), any());
    }

    @Test
    void esDisabledMeansPostgresOnly() {
        when(elasticsearch.isEnabled()).thenReturn(false);
        when(aggregates.query(anyString(), any(), any(), any())).thenReturn(List.of());

        service.queryAggregates("orders.completed", "1m", null, null);

        verify(elasticsearch, never()).query(anyString(), any(), any(), any());
    }

    @Test
    void createInsertsAndConflictIs409() {
        when(definitions.insert(any())).thenReturn(true).thenReturn(false);
        CreateMetricRequest request = new CreateMetricRequest("a.b", "A b", "count", null);

        MetricDefinition created = service.create(request);
        assertThat(created.metricKey()).isEqualTo("a.b");
        assertThat(created.createdAt()).isNotNull();

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
    }
}
