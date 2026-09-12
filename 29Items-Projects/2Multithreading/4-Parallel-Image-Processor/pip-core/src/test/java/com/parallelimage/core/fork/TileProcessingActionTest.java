package com.parallelimage.core.fork;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.parallelimage.core.model.Tile;
import com.parallelimage.core.pipeline.TileKernel;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentLinkedQueue;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link TileProcessingAction} tests that pin the leaf-vs-split decision and its collaborators
 * directly, without going through {@code ImageProcessingEngine} or real image I/O.
 *
 * <p>Every test here calls {@link TileProcessingAction#compute()} directly rather than submitting to
 * a {@link java.util.concurrent.ForkJoinPool}: {@code compute()} is {@code protected}, but the override
 * lives in this package, so a same-package test class can call it exactly like {@code ForkJoinPool}
 * would. For a leaf-sized tile this runs entirely on the calling thread; for a splittable tile,
 * {@code invokeAll} still forks onto the common pool internally but blocks until both halves join, so
 * by the time {@code compute()} returns every leaf has already run.
 */
class TileProcessingActionTest {

    @Nested
    @DisplayName("constructor")
    class Constructor {

        @Test
        @DisplayName("a cancelled, non-null token aborts compute() before the kernel ever runs")
        void cancelledTokenAbortsComputeBeforeTheKernel() {
            CancellationToken token = new CancellationToken();
            token.cancel();
            ConcurrentLinkedQueue<Tile> invoked = new ConcurrentLinkedQueue<>();
            TileKernel recordingKernel = (source, target, tile) -> invoked.add(tile);
            Tile tile = new Tile(0, 0, 4, 4);
            TileProcessingAction action = new TileProcessingAction(
                    newImage(4, 4), newImage(4, 4), recordingKernel, tile, 1_024L, token);

            assertThrows(CancellationException.class, action::compute,
                    "if the null-check ternary in the constructor is negated, a real non-null token "
                            + "would be swapped for CancellationToken.NONE and cancellation would be "
                            + "silently ignored");
            assertTrue(invoked.isEmpty(), "the kernel must never see a tile once cancelled");
        }

        @Test
        @DisplayName("a null token defaults to NONE and never blocks compute()")
        void nullTokenDefaultsToNoneAndDoesNotBlock() {
            ConcurrentLinkedQueue<Tile> invoked = new ConcurrentLinkedQueue<>();
            TileKernel recordingKernel = (source, target, tile) -> invoked.add(tile);
            Tile tile = new Tile(0, 0, 4, 4);
            TileProcessingAction action = new TileProcessingAction(
                    newImage(4, 4), newImage(4, 4), recordingKernel, tile, 1_024L, null);

            action.compute();

            assertEquals(1, invoked.size());
        }
    }

    @Nested
    @DisplayName("isLeaf, via compute()'s split behaviour")
    class LeafDecision {

        @Test
        @DisplayName("pixelCount == thresholdPixels is still a leaf (<=, not <)")
        void tileAtExactThresholdIsALeaf() {
            long threshold = TileProcessingAction.MIN_USEFUL_THRESHOLD_PIXELS;
            ConcurrentLinkedQueue<Tile> invoked = new ConcurrentLinkedQueue<>();
            TileKernel recordingKernel = (source, target, tile) -> invoked.add(tile);
            // width > 1, so this tile is splittable() == true: only the size comparison decides.
            Tile tile = new Tile(0, 0, (int) threshold, 1);
            TileProcessingAction action = new TileProcessingAction(
                    newImage(tile.width(), 1), newImage(tile.width(), 1), recordingKernel, tile,
                    threshold, null);

            action.compute();

            assertEquals(List.of(tile), List.copyOf(invoked),
                    "a boundary mutant (<= to <) or a 'replaced boolean return with true/false' mutant "
                            + "would either split this tile or misreport it; either way the recorded "
                            + "leaf tiles would not be exactly [the original tile]");
        }

        @Test
        @DisplayName("a splittable tile twice the threshold is split into exactly two leaves")
        void tileLargerThanThresholdSplitsInTwo() {
            long threshold = TileProcessingAction.MIN_USEFUL_THRESHOLD_PIXELS;
            ConcurrentLinkedQueue<Tile> invoked = new ConcurrentLinkedQueue<>();
            TileKernel recordingKernel = (source, target, tile) -> invoked.add(tile);
            Tile tile = new Tile(0, 0, (int) (threshold * 2), 1);
            TileProcessingAction action = new TileProcessingAction(
                    newImage(tile.width(), 1), newImage(tile.width(), 1), recordingKernel, tile,
                    threshold, null);

            action.compute();

            List<Tile> leaves = List.copyOf(invoked);
            assertEquals(2, leaves.size(),
                    "isLeaf() must say false once for the root tile so it splits; a 'replaced boolean "
                            + "return with true' or a negated !splittable()/negated AdaptiveThrottle "
                            + "term mutant would instead process the whole tile as a single leaf");
            long totalPixels = leaves.stream().mapToLong(Tile::pixelCount).sum();
            assertEquals(tile.pixelCount(), totalPixels,
                    "the two leaves must cover the whole tile with no overlap and no gap");
            for (Tile leaf : leaves) {
                assertTrue(leaf.pixelCount() <= threshold, "each half must itself be at or under the threshold");
            }
        }
    }

    @Nested
    @DisplayName("accessors")
    class Accessors {

        @Test
        @DisplayName("tile() exposes the exact tile instance the action was constructed with")
        void tileAccessorReturnsConstructorTile() {
            Tile tile = new Tile(2, 3, 4, 5);
            TileProcessingAction action = new TileProcessingAction(
                    newImage(10, 10), newImage(10, 10), (source, target, t) -> { }, tile, 2_048L, null);

            assertSame(tile, action.tile(), "replaced return value with null must be caught here");
        }

        @Test
        @DisplayName("toString reports the kernel name and the tile")
        void toStringIncludesKernelNameAndTile() {
            Tile tile = new Tile(0, 0, 8, 8);
            TileKernel kernel = new TileKernel() {
                @Override
                public void apply(BufferedImage source, BufferedImage target, Tile t) {
                    // no-op probe kernel
                }

                @Override
                public String name() {
                    return "ProbeKernel";
                }
            };
            TileProcessingAction action = new TileProcessingAction(
                    newImage(8, 8), newImage(8, 8), kernel, tile, 2_048L, null);

            String description = action.toString();

            assertTrue(description.contains("ProbeKernel"), description);
            assertTrue(description.contains(tile.toString()), description);
        }
    }

    @Nested
    @DisplayName("TileProcessingEvent (JFR)")
    class JfrEvent {

        @Test
        @DisplayName("a leaf commits exactly one event while JFR is recording, with begin/end bracketing real work")
        void leafCommitsJfrEventWhenRecordingIsActive() throws Exception {
            Path dump = Files.createTempFile("tile-processing-event", ".jfr");
            try (Recording recording = new Recording()) {
                recording.enable("com.parallelimage.core.jfr.TileProcessingEvent").withoutThreshold();

                TileKernel slowKernel = (source, target, tile) -> {
                    try {
                        Thread.sleep(60);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                };
                Tile tile = new Tile(0, 0, 4, 4);
                TileProcessingAction action = new TileProcessingAction(
                        newImage(4, 4), newImage(4, 4), slowKernel, tile, 1_024L, null);

                recording.start();
                action.compute();
                recording.stop();
                recording.dump(dump);

                List<RecordedEvent> events = RecordingFile.readAllEvents(dump);
                assertEquals(1, events.size(),
                        "exactly one leaf event must be committed while its recording is active; zero "
                                + "means the shouldCommit() check was negated or commit() was removed");
                RecordedEvent event = events.get(0);
                assertTrue(event.getDuration().toMillis() >= 40,
                        "begin()/end() must bracket the kernel's ~60ms of real work; a near-zero "
                                + "duration means one of those calls was removed");
            } finally {
                Files.deleteIfExists(dump);
            }
        }
    }

    private static BufferedImage newImage(int width, int height) {
        return new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    }
}
