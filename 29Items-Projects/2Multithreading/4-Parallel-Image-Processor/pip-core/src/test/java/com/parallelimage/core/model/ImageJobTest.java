package com.parallelimage.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** {@link ImageJob} tests. */
class ImageJobTest {

    private static final ProcessingOptions OPTIONS = ProcessingOptions.defaults();

    private static ImageJob newJob(Path source, Path target, JobStatus status) {
        return new ImageJob("job-1", "batch-1", source, target, OPTIONS, status);
    }

    @Nested
    @DisplayName("canonical constructor validation")
    class Validation {

        @Test
        @DisplayName("rejects a blank id")
        void rejectsBlankId() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ImageJob(" ", "batch-1", Path.of("a.png"), Path.of("b.png"), OPTIONS,
                            JobStatus.PENDING));
        }

        @Test
        @DisplayName("rejects a blank batchId")
        void rejectsBlankBatchId() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ImageJob("job-1", "", Path.of("a.png"), Path.of("b.png"), OPTIONS,
                            JobStatus.PENDING));
        }

        @Test
        @DisplayName("rejects a null source")
        void rejectsNullSource() {
            assertThrows(NullPointerException.class,
                    () -> new ImageJob("job-1", "batch-1", null, Path.of("b.png"), OPTIONS,
                            JobStatus.PENDING));
        }

        @Test
        @DisplayName("rejects a null target")
        void rejectsNullTarget() {
            assertThrows(NullPointerException.class,
                    () -> new ImageJob("job-1", "batch-1", Path.of("a.png"), null, OPTIONS,
                            JobStatus.PENDING));
        }

        @Test
        @DisplayName("rejects null options")
        void rejectsNullOptions() {
            assertThrows(NullPointerException.class,
                    () -> new ImageJob("job-1", "batch-1", Path.of("a.png"), Path.of("b.png"), null,
                            JobStatus.PENDING));
        }

        @Test
        @DisplayName("rejects a null status")
        void rejectsNullStatus() {
            assertThrows(NullPointerException.class,
                    () -> new ImageJob("job-1", "batch-1", Path.of("a.png"), Path.of("b.png"), OPTIONS,
                            null));
        }

        @Test
        @DisplayName("rejects in-place processing where source equals target")
        void rejectsInPlaceProcessing() {
            Path same = Path.of("same.png");
            assertThrows(IllegalArgumentException.class,
                    () -> new ImageJob("job-1", "batch-1", same, same, OPTIONS, JobStatus.PENDING));
        }

        @Test
        @DisplayName("accepts distinct source and target")
        void acceptsDistinctPaths() {
            ImageJob job = newJob(Path.of("a.png"), Path.of("b.png"), JobStatus.PENDING);
            assertEquals(Path.of("a.png"), job.source());
            assertEquals(Path.of("b.png"), job.target());
        }
    }

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("produces a PENDING job")
        void producesPendingJob() {
            ImageJob job = ImageJob.create("batch-1", Path.of("a.png"), Path.of("b.png"), OPTIONS);
            assertEquals(JobStatus.PENDING, job.status());
        }

        @Test
        @DisplayName("generates a non-blank, unique id per call")
        void generatesUniqueId() {
            ImageJob first = ImageJob.create("batch-1", Path.of("a.png"), Path.of("b.png"), OPTIONS);
            ImageJob second = ImageJob.create("batch-1", Path.of("a.png"), Path.of("b.png"), OPTIONS);
            assertNotNull(first.id());
            assertNotEquals(first.id(), second.id());
        }

        @Test
        @DisplayName("propagates the given batchId")
        void propagatesBatchId() {
            ImageJob job = ImageJob.create("batch-42", Path.of("a.png"), Path.of("b.png"), OPTIONS);
            assertEquals("batch-42", job.batchId());
        }
    }

    @Nested
    @DisplayName("withStatus()")
    class WithStatus {

        @Test
        @DisplayName("rejects a null next status")
        void rejectsNullNext() {
            ImageJob job = newJob(Path.of("a.png"), Path.of("b.png"), JobStatus.PENDING);
            assertThrows(NullPointerException.class, () -> job.withStatus(null));
        }

        @Test
        @DisplayName("allows a legal transition and returns a new instance carrying it")
        void allowsLegalTransition() {
            ImageJob job = newJob(Path.of("a.png"), Path.of("b.png"), JobStatus.PENDING);
            ImageJob running = job.withStatus(JobStatus.RUNNING);
            assertEquals(JobStatus.RUNNING, running.status());
            assertEquals(job.id(), running.id());
            assertEquals(job.batchId(), running.batchId());
            assertEquals(job.source(), running.source());
            assertEquals(job.target(), running.target());
            assertEquals(job.options(), running.options());
        }

        @Test
        @DisplayName("allows re-affirming the same status without consulting canTransitionTo")
        void allowsSameStatusNoOp() {
            ImageJob job = newJob(Path.of("a.png"), Path.of("b.png"), JobStatus.COMPLETED);
            ImageJob again = job.withStatus(JobStatus.COMPLETED);
            assertEquals(JobStatus.COMPLETED, again.status());
        }

        @Test
        @DisplayName("rejects an illegal transition")
        void rejectsIllegalTransition() {
            ImageJob job = newJob(Path.of("a.png"), Path.of("b.png"), JobStatus.PENDING);
            assertThrows(IllegalStateException.class, () -> job.withStatus(JobStatus.COMPLETED));
        }

        @Test
        @DisplayName("rejects leaving a terminal COMPLETED status")
        void rejectsLeavingCompleted() {
            ImageJob job = newJob(Path.of("a.png"), Path.of("b.png"), JobStatus.COMPLETED);
            assertThrows(IllegalStateException.class, () -> job.withStatus(JobStatus.PENDING));
        }
    }

    @Nested
    @DisplayName("displayName()")
    class DisplayNameMethod {

        @Test
        @DisplayName("returns just the file name, not the full path")
        void returnsFileNameOnly() {
            ImageJob job = newJob(Path.of("some", "nested", "dir", "photo.png"), Path.of("out.png"),
                    JobStatus.PENDING);
            assertEquals("photo.png", job.displayName());
        }

        @Test
        @DisplayName("falls back to the path's own toString when there is no file name component")
        void fallsBackToPathToStringWhenNoFileName() {
            Path root = Path.of(".").toAbsolutePath().getRoot();
            ImageJob job = newJob(root, Path.of("out.png"), JobStatus.PENDING);
            assertEquals(root.toString(), job.displayName());
        }
    }
}
