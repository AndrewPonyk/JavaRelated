package com.parallelimage.core.pipeline;

import com.parallelimage.core.model.Tile;
import java.awt.image.BufferedImage;

/**
 * A pixel transform that can be applied to one {@link Tile} independently of all other tiles.
 *
 * <h2>The contract every implementation must honour</h2>
 * <ol>
 *   <li><b>Read {@code source} freely</b> — including outside {@code tile}, which is what makes
 *       convolution halos work. {@code source} is never mutated during a tile pass.</li>
 *   <li><b>Write only inside {@code tile}</b>, and only into {@code target}. Writing a single pixel
 *       outside the tile is a data race with a sibling task; it will pass tests on one machine and
 *       corrupt output on another.</li>
 *   <li><b>Hold no mutable state.</b> One kernel instance is shared by every worker. Any per-tile
 *       scratch buffer must be a local variable inside {@link #apply}.</li>
 *   <li><b>Never synchronize.</b> If a kernel needs a lock, its decomposition is wrong.</li>
 * </ol>
 *
 * <p>Given (1)–(4), no memory barrier is needed between tile tasks: the {@code join()} inside
 * {@link com.parallelimage.core.fork.TileProcessingAction} establishes the happens-before edge that
 * publishes every tile's writes to the thread that eventually reads the finished image.
 *
 * <p>Implementations are expected to access pixels through the backing {@code int[]} of a
 * {@code DataBufferInt} rather than {@code getRGB}/{@code setRGB}, which are orders of magnitude
 * slower (TECH-NOTES §3.6 E4).
 */
@FunctionalInterface
public interface TileKernel {

    /**
     * Transforms the pixels of {@code tile}.
     *
     * @param source read-only input, guaranteed {@code TYPE_INT_RGB} or {@code TYPE_INT_ARGB}
     * @param target output; only the region described by {@code tile} may be written
     * @param tile   region to process; always fully inside both images' bounds
     */
    void apply(BufferedImage source, BufferedImage target, Tile tile);

    /** Name for logs and progress events. Defaults to the implementation's simple class name. */
    default String name() {
        return getClass().getSimpleName();
    }
}
