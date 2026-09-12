package com.parallelimage.core.model;

import com.parallelimage.core.util.Preconditions;

/**
 * An axis-aligned rectangular region of an image — the unit of Level-2 parallelism.
 *
 * <p>Immutable value object. {@link #split()} is the divide step of the divide-and-conquer
 * decomposition performed by {@link com.parallelimage.core.fork.TileProcessingAction}.
 *
 * <h2>Split invariants</h2>
 * For any splittable tile {@code t}, with {@code [a, b] = t.split()}:
 * <ul>
 *   <li><b>Total coverage:</b> {@code a.pixelCount() + b.pixelCount() == t.pixelCount()}</li>
 *   <li><b>No overlap:</b> {@code a} and {@code b} are disjoint</li>
 *   <li><b>No empty halves:</b> both have width &gt; 0 and height &gt; 0</li>
 * </ul>
 * These three properties are what make it safe for tile tasks to write concurrently into a shared
 * destination image with no locking whatsoever — each writes only its own pixels.
 *
 * @param x      left edge, inclusive
 * @param y      top edge, inclusive
 * @param width  horizontal extent in pixels, &gt; 0
 * @param height vertical extent in pixels, &gt; 0
 */
public record Tile(int x, int y, int width, int height) {

    public Tile {
        Preconditions.requireNonNegative(x, "x");
        Preconditions.requireNonNegative(y, "y");
        Preconditions.requirePositive(width, "width");
        Preconditions.requirePositive(height, "height");
    }

    /** A tile covering an entire image of the given dimensions. */
    public static Tile whole(int imageWidth, int imageHeight) {
        return new Tile(0, 0, imageWidth, imageHeight);
    }

    /** Exclusive right edge. */
    public int maxX() {
        return x + width;
    }

    /** Exclusive bottom edge. */
    public int maxY() {
        return y + height;
    }

    /** Area in pixels. {@code long} because a 100 MP image overflows nothing but is close enough. */
    public long pixelCount() {
        return (long) width * (long) height;
    }

    /** {@code true} when at least one axis has extent &ge; 2 and can therefore be halved. */
    public boolean splittable() {
        return height > 1 || width > 1;
    }

    /**
     * Halves this tile.
     *
     * <p><strong>Row-band split is preferred</strong> (splitting {@code height}) because scanlines
     * are contiguous in a {@code DataBufferInt}, so each half touches one contiguous memory range —
     * far kinder to the prefetcher and to cache lines shared between workers. Column splitting is
     * used only when the tile is a single row tall, which guarantees the recursion always makes
     * progress for any shape, including a 20 000&times;1 strip.
     *
     * @return exactly two disjoint tiles whose union is this tile
     * @throws IllegalStateException if {@code !splittable()}
     */
    public Tile[] split() {
        Preconditions.requireState(splittable(), "tile is 1x1 and cannot be split: " + this);
        if (height > 1) {
            int topHeight = height >>> 1;
            return new Tile[] {
                new Tile(x, y, width, topHeight),
                new Tile(x, y + topHeight, width, height - topHeight),
            };
        }
        int leftWidth = width >>> 1;
        return new Tile[] {
            new Tile(x, y, leftWidth, height),
            new Tile(x + leftWidth, y, width - leftWidth, height),
        };
    }

    /**
     * Clamps a coordinate to this tile's bounds. Used by convolution kernels that read a halo of
     * neighbouring pixels and must not walk off the edge.
     */
    public static int clamp(int value, int minInclusive, int maxExclusive) {
        if (value < minInclusive) {
            return minInclusive;
        }
        return Math.min(value, maxExclusive - 1);
    }

    @Override
    public String toString() {
        return "Tile[" + x + "," + y + " " + width + "x" + height + "]";
    }
}
