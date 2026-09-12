package com.parallelimage.core.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.parallelimage.core.error.ImageIoException;
import com.parallelimage.core.model.ImageJob;
import com.parallelimage.core.model.ProcessingOptions;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link ImageDiscovery} tests: extension filtering, recursive vs. flat walks, sorted output, and
 * the {@link ImageDiscovery#plan} batch-building path.
 */
class ImageDiscoveryTest {

    @TempDir
    private Path root;

    private Path touch(String relative) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "not a real image, only the name is inspected");
        return file;
    }

    @Nested
    @DisplayName("find")
    class Find {

        @Test
        @DisplayName("a root that does not exist is reported as an ImageIoException, not an IOException")
        void nonexistentRootThrows() {
            Path missing = root.resolve("does-not-exist");
            assertThrows(ImageIoException.class, () -> ImageDiscovery.find(missing, true, null));
        }

        @Test
        @DisplayName("a single file root yields itself when its extension is accepted")
        void singleFileRootAccepted() throws IOException {
            Path file = touch("photo.jpg");
            assertEquals(List.of(file), ImageDiscovery.find(file, true, null));
        }

        @Test
        @DisplayName("a single file root yields nothing when its extension is not accepted")
        void singleFileRootRejected() throws IOException {
            Path file = touch("notes.txt");
            assertEquals(List.of(), ImageDiscovery.find(file, true, null));
        }

        @Test
        @DisplayName("non-recursive walk only sees the top level")
        void nonRecursiveExcludesNested() throws IOException {
            touch("top.png");
            touch("nested/deep.png");

            List<Path> found = ImageDiscovery.find(root, false, null);

            assertEquals(1, found.size());
            assertEquals("top.png", found.get(0).getFileName().toString());
        }

        @Test
        @DisplayName("recursive walk descends into subdirectories")
        void recursiveIncludesNested() throws IOException {
            touch("top.png");
            touch("nested/deep.png");

            List<Path> found = ImageDiscovery.find(root, true, null);

            assertEquals(2, found.size());
        }

        @Test
        @DisplayName("a custom extension set replaces the default, entirely")
        void customExtensionsFilter() throws IOException {
            touch("a.png");
            Path raw = touch("b.raw");
            touch("c.jpg");

            List<Path> found = ImageDiscovery.find(root, false, Set.of("raw"));

            assertEquals(List.of(raw), found);
        }

        @Test
        @DisplayName("an empty extension set falls back to the defaults rather than matching nothing")
        void emptyExtensionSetFallsBackToDefaults() throws IOException {
            Path png = touch("a.png");

            assertEquals(List.of(png), ImageDiscovery.find(root, false, Set.of()));
        }

        @Test
        @DisplayName("results are sorted by path regardless of filesystem enumeration order")
        void resultsAreSorted() throws IOException {
            touch("c.png");
            touch("a.png");
            touch("b.png");

            List<Path> found = ImageDiscovery.find(root, false, null);

            List<Path> sorted = found.stream().sorted().toList();
            assertEquals(sorted, found);
        }

        @Test
        @DisplayName("a file with no extension is excluded")
        void fileWithNoExtensionIsExcluded() throws IOException {
            touch("README");
            assertEquals(List.of(), ImageDiscovery.find(root, false, null));
        }

        @Test
        @DisplayName("a file whose name ends in a bare dot is excluded")
        void fileEndingInDotIsExcluded() throws IOException {
            touch("weird.");
            assertEquals(List.of(), ImageDiscovery.find(root, false, null));
        }

        @Test
        @DisplayName("extension matching is case-insensitive")
        void extensionMatchIsCaseInsensitive() throws IOException {
            Path file = touch("photo.JPG");
            assertEquals(List.of(file), ImageDiscovery.find(root, false, null));
        }
    }

    @Nested
    @DisplayName("plan")
    class Plan {

        @Test
        @DisplayName("every job in a plan shares one batch id and mirrors the input tree under the output root")
        void sharesBatchIdAndMirrorsOutputTree() throws IOException {
            touch("album/a.png");
            touch("album/b.png");
            Path output = root.resolve("out");

            List<ImageJob> jobs = ImageDiscovery.plan(
                    root, output, ProcessingOptions.defaults(), true);

            assertEquals(2, jobs.size());
            String batchId = jobs.get(0).batchId();
            assertTrue(jobs.stream().allMatch(job -> job.batchId().equals(batchId)));
            assertTrue(jobs.stream()
                    .allMatch(job -> job.target().startsWith(output.resolve("album"))));
        }

        @Test
        @DisplayName("a plan over an empty tree yields no jobs")
        void emptyTreeYieldsNoJobs() throws IOException {
            Path output = root.resolve("out");
            List<ImageJob> jobs = ImageDiscovery.plan(
                    root, output, ProcessingOptions.defaults(), true);
            assertEquals(List.of(), jobs);
        }
    }
}
