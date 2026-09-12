package com.example.pipeline.application.stage;

import com.example.pipeline.domain.AggregateResult;
import com.example.pipeline.domain.SensorEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.RecursiveTask;

/**
 * Divide-and-conquer aggregation of one batch, for a {@code ForkJoinPool}.
 *
 * <p>Splits the event range in half until it is at most {@code sequentialThreshold}
 * events, folds each leaf into a {@code sensorId -> AggregateResult} map, then
 * merges the partial maps on the way back up. This is legal precisely because
 * {@link AggregateResult#merge} is associative and commutative — see that class.
 *
 * <p><strong>Fork/join etiquette:</strong> only the left half is forked; the right
 * half is computed on the current thread. Forking both and joining both wastes a
 * worker that could have been doing half the work.
 *
 * <p><strong>Never block in here.</strong> Fork/join workers are a fixed resource;
 * a blocking call (IO, {@code queue.take()}) starves the pool. Queue reads happen
 * on the dispatcher thread in {@link AggregationStage}, never inside this task.
 *
 * <p><strong>Cutoff matters more than parallelism.</strong> Splitting down to
 * single events makes fork overhead dominate the arithmetic; 256–1024 is the usual
 * sweet spot.
 *
 * <p><strong>On {@code @SuppressWarnings("serial")}:</strong> {@code RecursiveTask} is
 * {@code Serializable}, so {@code -Xlint:all} objects to the non-serializable
 * {@code List<SensorEvent>} field. These tasks are submitted to an in-process pool and are
 * never serialised; making the field {@code transient} would be worse, because it would
 * silence a real warning by breaking the class if anyone ever did serialise one. The
 * warning is suppressed here rather than left standing so that a genuinely new
 * {@code -Xlint} warning is visible in a build log instead of lost among known ones.
 */
@SuppressWarnings("serial")
public final class AggregationTask extends RecursiveTask<Map<String, AggregateResult>> {

    private static final long serialVersionUID = 1L;

    private final List<SensorEvent> events;
    private final int from;
    private final int to;
    private final int sequentialThreshold;

    /**
     * @param events              events to aggregate; must not be mutated while the task runs
     * @param from                inclusive start index
     * @param to                  exclusive end index
     * @param sequentialThreshold range size at or below which no further splitting happens
     */
    public AggregationTask(List<SensorEvent> events, int from, int to, int sequentialThreshold) {
        this.events = Objects.requireNonNull(events, "events");
        if (from < 0 || to > events.size() || from > to) {
            throw new IllegalArgumentException(
                    "invalid range [" + from + "," + to + ") for " + events.size() + " events");
        }
        if (sequentialThreshold < 1) {
            throw new IllegalArgumentException("sequentialThreshold must be >= 1 but was " + sequentialThreshold);
        }
        this.from = from;
        this.to = to;
        this.sequentialThreshold = sequentialThreshold;
    }

    /** Convenience factory covering the whole list. */
    public static AggregationTask forEvents(List<SensorEvent> events, int sequentialThreshold) {
        Objects.requireNonNull(events, "events");
        return new AggregationTask(events, 0, events.size(), sequentialThreshold);
    }

    /**
     * Single-threaded reference implementation.
     *
     * <p>Its existence is what makes the parallel path testable: the parallel result
     * must equal this one for the same input, which is a far stronger assertion than
     * checking a few fields by hand.
     */
    public static Map<String, AggregateResult> aggregateSequentially(List<SensorEvent> events) {
        Objects.requireNonNull(events, "events");
        Map<String, AggregateResult> result = new HashMap<>();
        for (SensorEvent event : events) {
            accumulate(result, event);
        }
        return result;
    }

    /** Merges {@code source} into {@code target} per sensor and returns {@code target}. */
    public static Map<String, AggregateResult> mergeInto(Map<String, AggregateResult> target,
                                                        Map<String, AggregateResult> source) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(source, "source");
        source.forEach((sensorId, result) -> target.merge(sensorId, result, AggregateResult::merge));
        return target;
    }

    @Override
    protected Map<String, AggregateResult> compute() {
        int length = to - from;
        if (length <= sequentialThreshold) {
            return computeSequentially();
        }
        int mid = from + (length >>> 1);
        AggregationTask left = new AggregationTask(events, from, mid, sequentialThreshold);
        AggregationTask right = new AggregationTask(events, mid, to, sequentialThreshold);
        left.fork();
        Map<String, AggregateResult> rightResult = right.compute();
        Map<String, AggregateResult> leftResult = left.join();
        return mergeInto(leftResult, rightResult);
    }

    private Map<String, AggregateResult> computeSequentially() {
        Map<String, AggregateResult> result = new HashMap<>();
        for (int i = from; i < to; i++) {
            accumulate(result, events.get(i));
        }
        return result;
    }

    private static void accumulate(Map<String, AggregateResult> target, SensorEvent event) {
        // compute() avoids allocating a throwaway single-event aggregate per event.
        target.compute(event.sensorId(),
                (sensorId, current) -> current == null ? AggregateResult.of(event) : current.accumulate(event));
    }
}
