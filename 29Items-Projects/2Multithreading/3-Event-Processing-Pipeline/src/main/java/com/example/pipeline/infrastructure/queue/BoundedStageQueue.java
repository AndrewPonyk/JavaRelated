package com.example.pipeline.infrastructure.queue;

import com.example.pipeline.application.port.MessageChannel;
import com.example.pipeline.application.port.MetricsRecorder;
import com.example.pipeline.domain.PipelineMessage;
import com.example.pipeline.domain.PoisonPill;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * {@link MessageChannel} over an {@link ArrayBlockingQueue}, with blocked-time accounting.
 *
 * <p><strong>Why {@code ArrayBlockingQueue} and not {@code LinkedBlockingQueue}:</strong>
 * <ul>
 *   <li>its capacity is fixed at construction, so the memory bound of a hop is a
 *       number you can compute — {@code capacity * batchSize * sizeof(event)};</li>
 *   <li>the backing array is allocated once, so steady-state enqueue/dequeue does no
 *       node allocation and creates no GC pressure at high rates;</li>
 *   <li>an unbounded {@code LinkedBlockingQueue} would turn a slow consumer into a
 *       heap exhaustion instead of backpressure — the failure mode this design exists
 *       to avoid.</li>
 * </ul>
 *
 * <p>The single lock {@code ArrayBlockingQueue} uses for both ends is not the
 * bottleneck at this granularity: one lock acquisition per <em>batch</em> of 128
 * events is noise next to the filtering work. A per-event queue would be a different
 * conversation.
 *
 * <p><strong>Blocked-time accounting</strong> is the diagnostic that makes a stalled
 * pipeline legible: {@link #put} first tries a non-blocking {@code offer}, and only if
 * that fails does it time the blocking wait and report it. So the happy path pays
 * nothing, and the queue with the largest blocked time names the bottleneck — the
 * stage <em>downstream</em> of it is the constraint.
 */
public final class BoundedStageQueue implements MessageChannel {

    private final String name;
    private final BlockingQueue<PipelineMessage> queue;
    private final MetricsRecorder metrics;
    private final int capacity;

    /**
     * @param name     stable name for metrics, e.g. {@code producer->filter}
     * @param capacity maximum queued messages, {@code >= 1}
     * @param metrics  recorder for blocked time; use {@link MetricsRecorder#noOp()} to disable
     */
    public BoundedStageQueue(String name, int capacity, MetricsRecorder metrics) {
        this.name = Objects.requireNonNull(name, "name");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be >= 1 but was " + capacity);
        }
        this.capacity = capacity;
        // Non-fair: fairness costs ~2x throughput and buys ordering between *waiting
        // threads*, which no stage here depends on. FIFO of *messages* is unaffected.
        this.queue = new ArrayBlockingQueue<>(capacity);
    }

    @Override
    public void put(PipelineMessage message) throws InterruptedException {
        Objects.requireNonNull(message, "message");
        if (queue.offer(message)) {
            return;
        }
        long startNanos = System.nanoTime();
        queue.put(message);
        metrics.recordQueueBlocked(name, System.nanoTime() - startNanos);
    }

    @Override
    public PipelineMessage poll(Duration timeout) throws InterruptedException {
        Objects.requireNonNull(timeout, "timeout");
        return queue.poll(timeout.toNanos(), TimeUnit.NANOSECONDS);
    }

    @Override
    public void putPoisonPills(int count) throws InterruptedException {
        if (count < 0) {
            throw new IllegalArgumentException("count must be >= 0 but was " + count);
        }
        // One at a time through put(): pills must respect the same bound as data, or a
        // burst of pills could be the thing that finally exhausts memory.
        for (int i = 0; i < count; i++) {
            put(PoisonPill.INSTANCE);
        }
    }

    @Override
    public int depth() {
        return queue.size();
    }

    @Override
    public int capacity() {
        return capacity;
    }

    @Override
    public String name() {
        return name;
    }

    /** How full the queue is, {@code 0.0}–{@code 1.0}; the dashboard's saturation gauge. */
    public double utilisation() {
        return (double) queue.size() / capacity;
    }

    /** {@code name depth/capacity} — compact enough for a dashboard line. */
    @Override
    public String toString() {
        return name + " " + queue.size() + "/" + capacity;
    }
}
