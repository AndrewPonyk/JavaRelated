package com.rtap.streaming.aggregation;

import com.rtap.streaming.common.model.BusinessEvent;
import com.rtap.streaming.common.model.MetricAggregate;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Incremental per-window aggregation: state per (series × window) is a fixed-size
 * accumulator (count/sum/min/max + the series' dimension map) — O(1) per event, no
 * event buffering. This is what keeps 1s windows cheap at high throughput
 * (ARCHITECTURE.md §2.4).
 *
 * <p>Windows key on the <em>series</em>: {@code metricKey + '|' + dimensionsHash} —
 * one aggregate row per dimension combination. The API merges across dimensions at
 * query time (SUM/MIN/MAX group-by window), so totals stay correct.
 */
public class MetricAggregateFunction
        implements AggregateFunction<BusinessEvent, MetricAggregateFunction.MetricAccumulator, MetricAggregateFunction.MetricAccumulator> {

    private static final long serialVersionUID = 1L;

    /** Key selector shared by the job and tests: one window state per metric series. */
    public static String seriesKey(BusinessEvent event) {
        return event.getEventType() + '|' + MetricAggregate.hashDimensions(event.getDimensions());
    }

    /** Fixed-size accumulator; a Flink POJO (public fields, no-arg ctor). */
    public static class MetricAccumulator implements Serializable {
        private static final long serialVersionUID = 1L;

        public long count;
        public double sum;
        public double min = Double.POSITIVE_INFINITY;
        public double max = Double.NEGATIVE_INFINITY;
        public Map<String, String> dimensions; // constant within a series — captured once
    }

    @Override
    public MetricAccumulator createAccumulator() {
        return new MetricAccumulator();
    }

    @Override
    public MetricAccumulator add(BusinessEvent event, MetricAccumulator acc) {
        acc.count++;
        acc.sum += event.getValue();
        acc.min = Math.min(acc.min, event.getValue());
        acc.max = Math.max(acc.max, event.getValue());
        if (acc.dimensions == null) {
            acc.dimensions = new HashMap<>(event.getDimensions());
        }
        return acc;
    }

    @Override
    public MetricAccumulator getResult(MetricAccumulator acc) {
        return acc; // window metadata is attached by ToMetricAggregate
    }

    @Override
    public MetricAccumulator merge(MetricAccumulator a, MetricAccumulator b) {
        a.count += b.count;
        a.sum += b.sum;
        a.min = Math.min(a.min, b.min);
        a.max = Math.max(a.max, b.max);
        if (a.dimensions == null) {
            a.dimensions = b.dimensions;
        }
        return a;
    }

    /** Attaches key + window bounds to the accumulator → emits the final {@link MetricAggregate}. */
    public static class ToMetricAggregate
            extends ProcessWindowFunction<MetricAccumulator, MetricAggregate, String, TimeWindow> {

        private static final long serialVersionUID = 1L;
        private final String windowSize;

        public ToMetricAggregate(String windowSize) {
            this.windowSize = windowSize;
        }

        @Override
        public void process(String seriesKey, Context context,
                            Iterable<MetricAccumulator> accumulators, Collector<MetricAggregate> out) {
            MetricAccumulator acc = accumulators.iterator().next(); // exactly one — incremental aggregation
            MetricAggregate agg = new MetricAggregate();
            agg.setMetricKey(metricKeyOf(seriesKey));
            agg.setWindowSize(windowSize);
            agg.setWindowStart(context.window().getStart());
            agg.setWindowEnd(context.window().getEnd());
            agg.setCount(acc.count);
            agg.setSum(acc.sum);
            agg.setMin(acc.min);
            agg.setMax(acc.max);
            if (acc.dimensions != null) {
                agg.setDimensions(acc.dimensions);
            }
            out.collect(agg);
        }
    }

    /** The series key is {@code metricKey|dimHash}; metric keys never contain '|'. */
    static String metricKeyOf(String seriesKey) {
        int cut = seriesKey.lastIndexOf('|');
        return cut < 0 ? seriesKey : seriesKey.substring(0, cut);
    }

    /** Pairwise merge of aggregates — used by the 1m rollup reduce. */
    public static MetricAggregate merge(MetricAggregate a, MetricAggregate b) {
        MetricAggregate m = new MetricAggregate();
        m.setMetricKey(a.getMetricKey());
        m.setDimensions(a.getDimensions());
        m.setCount(a.getCount() + b.getCount());
        m.setSum(a.getSum() + b.getSum());
        m.setMin(Math.min(a.getMin(), b.getMin()));
        m.setMax(Math.max(a.getMax(), b.getMax()));
        return m;
    }

    /** Re-stamps rollup output with the enclosing (e.g. 1m) window bounds and label. */
    public static class RestampWindow
            extends ProcessWindowFunction<MetricAggregate, MetricAggregate, String, TimeWindow> {

        private static final long serialVersionUID = 1L;
        private final String windowSize;

        public RestampWindow(String windowSize) {
            this.windowSize = windowSize;
        }

        @Override
        public void process(String seriesKey, Context context,
                            Iterable<MetricAggregate> merged, Collector<MetricAggregate> out) {
            MetricAggregate agg = merged.iterator().next();
            agg.setWindowSize(windowSize);
            agg.setWindowStart(context.window().getStart());
            agg.setWindowEnd(context.window().getEnd());
            out.collect(agg);
        }
    }
}
