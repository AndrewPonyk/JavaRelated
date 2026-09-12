package com.rtap.streaming.aggregation;

import com.rtap.streaming.common.model.BusinessEvent;
import com.rtap.streaming.common.model.MetricAggregate;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Pure unit tests — the aggregation core is testable without any Flink runtime. */
class MetricAggregateFunctionTest {

    private final MetricAggregateFunction fn = new MetricAggregateFunction();

    private static BusinessEvent event(double value) {
        return event(value, Map.of("region", "eu"));
    }

    private static BusinessEvent event(double value, Map<String, String> dims) {
        BusinessEvent e = new BusinessEvent();
        e.setEventId("e-" + value);
        e.setEventType("orders.completed");
        e.setOccurredAt(1_700_000_000_000L);
        e.setValue(value);
        e.setDimensions(dims);
        return e;
    }

    @Test
    void accumulatesCountSumMinMaxAndCapturesDimensions() {
        var acc = fn.createAccumulator();
        acc = fn.add(event(10.0), acc);
        acc = fn.add(event(2.5), acc);
        acc = fn.add(event(7.5), acc);

        assertThat(acc.count).isEqualTo(3);
        assertThat(acc.sum).isEqualTo(20.0);
        assertThat(acc.min).isEqualTo(2.5);
        assertThat(acc.max).isEqualTo(10.0);
        assertThat(acc.dimensions).containsEntry("region", "eu");
    }

    @Test
    void mergesAccumulators() {
        var a = fn.add(event(1.0), fn.createAccumulator());
        var b = fn.add(event(9.0), fn.createAccumulator());

        var merged = fn.merge(a, b);

        assertThat(merged.count).isEqualTo(2);
        assertThat(merged.sum).isEqualTo(10.0);
        assertThat(merged.min).isEqualTo(1.0);
        assertThat(merged.max).isEqualTo(9.0);
    }

    @Test
    void seriesKeySeparatesDimensionCombinations() {
        String eu = MetricAggregateFunction.seriesKey(event(1.0, Map.of("region", "eu")));
        String us = MetricAggregateFunction.seriesKey(event(1.0, Map.of("region", "us")));
        String euAgain = MetricAggregateFunction.seriesKey(event(9.9, Map.of("region", "eu")));

        assertThat(eu).isNotEqualTo(us);
        assertThat(eu).isEqualTo(euAgain);
        assertThat(MetricAggregateFunction.metricKeyOf(eu)).isEqualTo("orders.completed");
    }

    @Test
    void rollupMergeKeepsKeyAndCombinesStats() {
        MetricAggregate a = aggregate(5, 50.0, 1.0, 20.0);
        MetricAggregate b = aggregate(3, 30.0, 0.5, 25.0);

        MetricAggregate m = MetricAggregateFunction.merge(a, b);

        assertThat(m.getMetricKey()).isEqualTo("orders.completed");
        assertThat(m.getCount()).isEqualTo(8);
        assertThat(m.getSum()).isEqualTo(80.0);
        assertThat(m.getMin()).isEqualTo(0.5);
        assertThat(m.getMax()).isEqualTo(25.0);
    }

    /** documentId is the idempotence contract — deterministic and dimension-order independent. */
    @Test
    void documentIdIsDeterministicAndOrderInsensitive() {
        MetricAggregate x = aggregate(1, 1.0, 1.0, 1.0);
        x.setDimensions(Map.of("region", "eu", "channel", "web"));
        MetricAggregate y = aggregate(1, 1.0, 1.0, 1.0);
        y.setDimensions(Map.of("channel", "web", "region", "eu")); // same map, different insertion order

        assertThat(x.documentId()).isEqualTo(y.documentId());
        assertThat(x.documentId()).startsWith("orders.completed|1s|1700000000000|");
        assertThat(x.documentId()).isNotEqualTo(aggregate(1, 1.0, 1.0, 1.0).documentId()); // dims matter
    }

    private static MetricAggregate aggregate(long count, double sum, double min, double max) {
        MetricAggregate agg = new MetricAggregate();
        agg.setMetricKey("orders.completed");
        agg.setWindowSize("1s");
        agg.setWindowStart(1_700_000_000_000L);
        agg.setWindowEnd(1_700_000_001_000L);
        agg.setCount(count);
        agg.setSum(sum);
        agg.setMin(min);
        agg.setMax(max);
        return agg;
    }
}
