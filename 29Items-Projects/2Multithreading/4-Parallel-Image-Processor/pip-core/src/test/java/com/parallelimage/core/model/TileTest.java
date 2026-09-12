package com.parallelimage.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The split invariants are the foundation everything else stands on: if two tiles could ever overlap,
 * every lock-free kernel in {@code filter/} would be racy. These tests are therefore not "coverage" —
 * they are the proof obligation for the whole Level-2 design.
 */
class TileTest {

    @Test
    @DisplayName("whole() covers the image exactly")
    void wholeCoversImage() {
        Tile tile = Tile.whole(800, 600);
        assertEquals(0, tile.x());
        assertEquals(0, tile.y());
        assertEquals(800, tile.maxX());
        assertEquals(600, tile.maxY());
        assertEquals(480_000L, tile.pixelCount());
    }

    @Test
    @DisplayName("split() prefers row bands, because scanlines are contiguous in memory")
    void splitPrefersRowBands() {
        Tile[] halves = new Tile(0, 0, 100, 10).split();
        assertEquals(100, halves[0].width(), "width must be preserved by a row-band split");
        assertEquals(100, halves[1].width());
        assertEquals(5, halves[0].height());
        assertEquals(5, halves[1].height());
    }

    @Test
    @DisplayName("split() falls back to columns for a single-row strip, so recursion always progresses")
    void splitFallsBackToColumns() {
        Tile[] halves = new Tile(0, 0, 20_000, 1).split();
        assertEquals(1, halves[0].height());
        assertEquals(10_000, halves[0].width());
        assertEquals(10_000, halves[1].width());
        assertEquals(10_000, halves[1].x());
    }

    @Test
    @DisplayName("a 1x1 tile is not splittable and says so instead of looping forever")
    void unsplittableTileThrows() {
        Tile pixel = new Tile(3, 4, 1, 1);
        assertFalse(pixel.splittable());
        assertThrows(IllegalStateException.class, pixel::split);
    }

    @ParameterizedTest(name = "{0}x{1} recursive split covers every pixel exactly once")
    @CsvSource({"1,1", "1,7", "7,1", "16,16", "17,13", "1920,1080", "4001,3999"})
    @DisplayName("recursive splitting partitions the image: total coverage, no overlap, no gaps")
    void recursiveSplitPartitionsExactly(int width, int height) {
        List<Tile> leaves = splitToPixels(Tile.whole(width, height));

        // Total coverage: the leaf areas sum to the image area.
        long covered = leaves.stream().mapToLong(Tile::pixelCount).sum();
        assertEquals((long) width * height, covered, "leaf areas must sum to the image area");

        // No overlap and no gaps: every pixel is claimed exactly once.
        boolean[] seen = new boolean[width * height];
        for (Tile leaf : leaves) {
            for (int y = leaf.y(); y < leaf.maxY(); y++) {
                for (int x = leaf.x(); x < leaf.maxX(); x++) {
                    int index = y * width + x;
                    assertFalse(seen[index], "pixel " + x + "," + y + " claimed twice");
                    seen[index] = true;
                }
            }
        }
        for (int i = 0; i < seen.length; i++) {
            assertTrue(seen[i], "pixel index " + i + " was never covered");
        }
    }

    @Test
    @DisplayName("clamp() keeps a halo read inside the raster")
    void clampKeepsHaloInBounds() {
        assertEquals(0, Tile.clamp(-5, 0, 10));
        assertEquals(9, Tile.clamp(99, 0, 10));
        assertEquals(4, Tile.clamp(4, 0, 10));
    }

    @Test
    @DisplayName("degenerate dimensions are rejected at construction, not at pixel-write time")
    void invalidDimensionsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Tile(0, 0, 0, 10));
        assertThrows(IllegalArgumentException.class, () -> new Tile(0, 0, 10, 0));
        assertThrows(IllegalArgumentException.class, () -> new Tile(-1, 0, 10, 10));
    }

    /** Splits all the way down to 1x1 leaves — the strongest possible form of the invariant check. */
    private static List<Tile> splitToPixels(Tile root) {
        List<Tile> leaves = new ArrayList<>();
        Deque<Tile> pending = new ArrayDeque<>();
        pending.push(root);
        while (!pending.isEmpty()) {
            Tile tile = pending.pop();
            if (!tile.splittable()) {
                leaves.add(tile);
                continue;
            }
            for (Tile half : tile.split()) {
                pending.push(half);
            }
        }
        return leaves;
    }
}
