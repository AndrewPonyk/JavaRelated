package com.parallelimage.core.model;

import com.parallelimage.core.util.Preconditions;
import java.nio.file.Path;

/**
 * The terminal result of one {@link ImageJob}.
 *
 * <p><strong>Failures are data, not control flow.</strong> A leaf of
 * {@link com.parallelimage.core.fork.BatchProcessingTask} catches whatever the pipeline threw and
 * converts it into {@link Failure} so the exception never travels up the fork/join tree. That
 * matters for two reasons:
 * <ol>
 *   <li>{@code ForkJoinTask} rethrows the <em>same exception instance</em> in the joining thread, so
 *       a propagating exception produces a misleading stack trace (see TECH-NOTES §3.6 A7).</li>
 *   <li>One corrupt JPEG must not abort a 10 000-image batch.</li>
 * </ol>
 *
 * <p>{@link Failure} intentionally stores the exception <em>type name and message</em> rather than a
 * {@link Throwable}: outcomes are aggregated into {@link BatchResult}, persisted, and compared, and
 * holding a live {@code Throwable} would keep the whole captured stack (and any objects it
 * references) reachable for the duration of the batch.
 */
public sealed interface JobOutcome {

    String jobId();

    /** Wall-clock time spent on this job, in nanoseconds. */
    long durationNanos();

    /** The image was written; {@code output} exists and is complete. */
    record Success(String jobId, Path output, long durationNanos, long pixelsProcessed)
            implements JobOutcome {

        public Success {
            Preconditions.requireNonBlank(jobId, "jobId");
            Preconditions.requireNonNull(output, "output");
            Preconditions.requireNonNegative(durationNanos, "durationNanos");
            Preconditions.requireNonNegative(pixelsProcessed, "pixelsProcessed");
        }
    }

    /** This one image failed; the rest of the batch continues, and the exit code becomes 1. */
    record Failure(String jobId, String reason, String exceptionType, String operationName,
            long durationNanos) implements JobOutcome {

        public Failure {
            Preconditions.requireNonBlank(jobId, "jobId");
            Preconditions.requireNonBlank(reason, "reason");
            Preconditions.requireNonBlank(exceptionType, "exceptionType");
            Preconditions.requireNonNegative(durationNanos, "durationNanos");
        }

        /** Builds a failure from a caught throwable without retaining the throwable itself. */
        public static Failure from(String jobId, Throwable cause, String operationName,
                long durationNanos) {
            String message = cause.getMessage();
            return new Failure(
                    jobId,
                    message == null || message.isBlank() ? cause.getClass().getSimpleName() : message,
                    cause.getClass().getName(),
                    operationName,
                    durationNanos);
        }
    }

    /** The job observed the cancellation token before finishing; no output was written. */
    record Cancelled(String jobId, long durationNanos) implements JobOutcome {

        public Cancelled {
            Preconditions.requireNonBlank(jobId, "jobId");
            Preconditions.requireNonNegative(durationNanos, "durationNanos");
        }
    }

    /** Maps this outcome onto the persisted job lifecycle. Exhaustive: no {@code default}. */
    default JobStatus toStatus() {
        return switch (this) {
            case Success s -> JobStatus.COMPLETED;
            case Failure f -> JobStatus.FAILED;
            case Cancelled c -> JobStatus.CANCELLED;
        };
    }
}
