package com.rtap.streaming.aggregation;

import com.rtap.streaming.common.model.BusinessEvent;
import com.rtap.streaming.common.model.MetricAggregate;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dataflow tests on an in-JVM local Flink environment (no Docker): the exact window
 * operators used by the job, driven with event-time inputs.
 */
class AggregationFlowTest {

    private static final long BASE = 1_700_000_000_000L; // aligned to 1s windows

    private static BusinessEvent event(String type, Map<String, String> dims, long at, double value) {
        BusinessEvent e = new BusinessEvent();
        e.setEventId(type + "-" + at + "-" + value);
        e.setEventType(type);
        e.setOccurredAt(at);
        e.setValue(value);
        e.setDimensions(dims);
        return e;
    }

    private static DataStream<BusinessEvent> streamOf(StreamExecutionEnvironment env, List<BusinessEvent> events) {
        return env.fromData(events)
                .assignTimestampsAndWatermarks(WatermarkStrategy
                        .<BusinessEvent>forMonotonousTimestamps()
                        .withTimestampAssigner((e, ts) -> e.getOccurredAt()));
    }

    @Test
    void oneSecondWindowsAggregatePerSeries() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        List<BusinessEvent> events = List.of(
                event("orders.completed", Map.of("region", "eu"), BASE + 100, 10.0),
                event("orders.completed", Map.of("region", "eu"), BASE + 400, 20.0),
                event("orders.completed", Map.of("region", "us"), BASE + 500, 100.0),
                event("orders.completed", Map.of("region", "eu"), BASE + 900, 5.0),
                event("orders.completed", Map.of("region", "eu"), BASE + 1200, 7.0));

        List<MetricAggregate> out =
                MetricsAggregationJob.perSecondAggregates(streamOf(env, events)).executeAndCollect(100);

        assertThat(out).hasSize(3);

        MetricAggregate euFirst = find(out, "eu", BASE);
        assertThat(euFirst.getCount()).isEqualTo(3);
        assertThat(euFirst.getSum()).isEqualTo(35.0);
        assertThat(euFirst.getMin()).isEqualTo(5.0);
        assertThat(euFirst.getMax()).isEqualTo(20.0);
        assertThat(euFirst.getWindowEnd()).isEqualTo(BASE + 1000);
        assertThat(euFirst.getWindowSize()).isEqualTo("1s");

        MetricAggregate usFirst = find(out, "us", BASE);
        assertThat(usFirst.getCount()).isEqualTo(1);
        assertThat(usFirst.getSum()).isEqualTo(100.0);

        MetricAggregate euSecond = find(out, "eu", BASE + 1000);
        assertThat(euSecond.getCount()).isEqualTo(1);
        assertThat(euSecond.getSum()).isEqualTo(7.0);
    }

    @Test
    void oneMinuteRollupReAggregatesTheOneSecondStream() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        List<BusinessEvent> events = List.of(
                event("orders.completed", Map.of("region", "eu"), BASE + 100, 10.0),
                event("orders.completed", Map.of("region", "eu"), BASE + 1500, 20.0),
                event("orders.completed", Map.of("region", "eu"), BASE + 2500, 30.0));

        List<MetricAggregate> out = MetricsAggregationJob
                .perMinuteRollup(MetricsAggregationJob.perSecondAggregates(streamOf(env, events)))
                .executeAndCollect(100);

        assertThat(out).hasSize(1);
        MetricAggregate minute = out.get(0);
        assertThat(minute.getWindowSize()).isEqualTo("1m");
        assertThat(minute.getMetricKey()).isEqualTo("orders.completed");
        assertThat(minute.getCount()).isEqualTo(3);
        assertThat(minute.getSum()).isEqualTo(60.0);
        assertThat(minute.getMin()).isEqualTo(10.0);
        assertThat(minute.getMax()).isEqualTo(30.0);
        assertThat(minute.getWindowEnd() - minute.getWindowStart()).isEqualTo(60_000L);
        assertThat(minute.getDimensions()).containsEntry("region", "eu");
    }

    private static MetricAggregate find(List<MetricAggregate> aggregates, String region, long windowStart) {
        return aggregates.stream()
                .filter(a -> region.equals(a.getDimensions().get("region")) && a.getWindowStart() == windowStart)
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing aggregate " + region + "@" + windowStart));
    }
}
