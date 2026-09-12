package com.parallelimage.core.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Aggregated result of a batch — the return type of
 * {@link com.parallelimage.core.fork.BatchProcessingTask}.
 *
 * <h2>Why this is a monoid</h2>
 * A {@code RecursiveTask} reduces results <em>bottom-up</em>, in an order determined by the
 * scheduler and by which worker happened to steal which subtree. For that reduction to be correct,
 * {@link #merge(BatchResult)} must be:
 * <ul>
 *   <li><b>associative</b> — {@code (a⊕b)⊕c == a⊕(b⊕c)}, because the tree shape varies</li>
 *   <li><b>commutative</b> in effect — join order is not deterministic</li>
 *   <li>equipped with an <b>identity</b> — {@link #EMPTY}, returned for an empty job range</li>
 * </ul>
 * Every field below is either a sum (counts, nanos, pixels) or a concatenation (outcomes), both of
 * which satisfy this. <strong>Do not add a field that depends on order</strong> (e.g. "first
 * failure", "last completed") without making it order-independent first — it will produce results
 * that differ run to run and look like a heisenbug.
 *
 * <p>{@code totalNanos} is the <em>sum of per-job times</em>, i.e. CPU-time-ish, and will exceed
 * wall-clock time under parallelism. That is intentional: dividing it by wall-clock gives the
 * achieved speedup.
 *
 * @param succeeded  count of {@link JobOutcome.Success}
 * @param failed     count of {@link JobOutcome.Failure}
 * @param cancelled  count of {@link JobOutcome.Cancelled}
 * @param totalNanos summed per-job durations
 * @param pixelsProcessed summed pixel count of successful jobs
 * @param outcomes   individual outcomes, unmodifiable
 */
public record BatchResult(
        int succeeded,
        int failed,
        int cancelled,
        long totalNanos,
        long pixelsProcessed,
        List<JobOutcome> outcomes) {

    /** Identity element of {@link #merge}. */
    public static final BatchResult EMPTY = new BatchResult(0, 0, 0, 0L, 0L, List.of());

    public BatchResult {
        outcomes = List.copyOf(outcomes);
    }

    /** Lifts a single outcome into a one-element result. */
    public static BatchResult of(JobOutcome outcome) {
        return switch (outcome) {
            case JobOutcome.Success s ->
                    new BatchResult(1, 0, 0, s.durationNanos(), s.pixelsProcessed(), List.of(s));
            case JobOutcome.Failure f ->
                    new BatchResult(0, 1, 0, f.durationNanos(), 0L, List.of(f));
            case JobOutcome.Cancelled c ->
                    new BatchResult(0, 0, 1, c.durationNanos(), 0L, List.of(c));
        };
    }

    /**
     * Combines two partial results. Associative, commutative, with {@link #EMPTY} as identity.
     *
     * <p>Short-circuits on identity to avoid copying the outcome list at every level of a deep
     * recursion tree — with a 10 000-job batch the tree is ~11 levels deep and naive concatenation
     * at every node is measurably wasteful.
     */
    public BatchResult merge(BatchResult other) {
        if (other == null || other.isEmpty()) {
            return this;
        }
        if (this.isEmpty()) {
            return other;
        }
        List<JobOutcome> combined = new ArrayList<>(outcomes.size() + other.outcomes.size());
        combined.addAll(outcomes);
        combined.addAll(other.outcomes);
        return new BatchResult(
                succeeded + other.succeeded,
                failed + other.failed,
                cancelled + other.cancelled,
                totalNanos + other.totalNanos,
                pixelsProcessed + other.pixelsProcessed,
                combined);
    }

    public boolean isEmpty() {
        return total() == 0;
    }

    public int total() {
        return succeeded + failed + cancelled;
    }

    public boolean hasFailures() {
        return failed > 0;
    }

    /** Only the failures — what the UI's "Retry failed" action and the exit code care about. */
    public List<JobOutcome.Failure> failures() {
        return outcomes.stream()
                .filter(JobOutcome.Failure.class::isInstance)
                .map(JobOutcome.Failure.class::cast)
                .toList();
    }

    /** Mean per-job duration in milliseconds, or 0 for an empty result. */
    public double averageMillis() {
        int n = total();
        return n == 0 ? 0.0d : (totalNanos / 1_000_000.0d) / n;
    }

    /**
     * Exit code contract for the headless CLI (see docs/PROJECT-PLAN.md §2 Phase 1.6):
     * {@code 0} all good, {@code 1} partial failure.
     */
    public int toExitCode() {
        return failed == 0 ? 0 : 1;
    }

    @Override
    public String toString() {
        return "BatchResult[ok=%d failed=%d cancelled=%d avg=%.1fms %.1fMP]"
                .formatted(succeeded, failed, cancelled, averageMillis(),
                        pixelsProcessed / 1_000_000.0d);
    }
}
