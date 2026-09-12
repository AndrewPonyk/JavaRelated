package com.parallelimage.core.fork;

import com.parallelimage.core.jfr.TileProcessingEvent;
import com.parallelimage.core.model.Tile;
import com.parallelimage.core.pipeline.TileKernel;
import com.parallelimage.core.util.Preconditions;
import java.awt.image.BufferedImage;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.concurrent.RecursiveAction;

/**
 * <strong>Level 2 of the decomposition:</strong> recursively halves one image into tiles until each
 * leaf is small enough to process sequentially, then runs a {@link TileKernel} on it.
 *
 * <p>A {@link RecursiveAction} rather than a {@code RecursiveTask} because there is nothing to
 * return — every leaf writes directly into its own disjoint region of the shared destination image.
 * That is the central design property; see {@link TileKernel} for the contract that makes it safe.
 *
 * <h2>Why this level exists at all</h2>
 * Level 1 ({@link BatchProcessingTask}) already parallelizes across images, which is enough when all
 * images are similar. It is <em>not</em> enough when they are not: a batch of 200 thumbnails plus one
 * 48-megapixel panorama finishes its 200 easy jobs quickly and then leaves N-1 cores idle while a
 * single worker grinds through the panorama. That serial tail is the dominant Amdahl term for real
 * photo libraries. Nesting a second decomposition inside the leaf turns the panorama into hundreds of
 * stealable sub-tasks, so every idle worker in the same pool joins in automatically — no scheduling
 * logic on our part.
 *
 * <h2>Fork/join idiom used here</h2>
 * {@code invokeAll(left, right)} rather than {@code left.fork(); right.fork(); left.join();
 * right.join();}. The latter wastes the current thread (it forks both halves then blocks) and inverts
 * the LIFO locality the work-stealing deque depends on. {@code invokeAll} forks one half, computes the
 * other on the current thread, and joins in the correct (reverse) order. See TECH-NOTES §3.6 A2/A3.
 *
 * <p><strong>Not reusable.</strong> A {@code ForkJoinTask} may be executed once; construct a fresh
 * tree per image per operation.
 */
public final class TileProcessingAction extends RecursiveAction {

    private static final long serialVersionUID = 1L;

    /** Below this leaf size, fork overhead exceeds the pixel work. See TECH-NOTES §3.6 A4. */
    public static final long MIN_USEFUL_THRESHOLD_PIXELS = 1_024L;

    private final transient BufferedImage source;
    private final transient BufferedImage target;
    private final transient TileKernel kernel;
    private final transient Tile tile;
    private final long thresholdPixels;
    private final transient CancellationToken token;

    /**
     * Creates the root action for one image; {@link #compute()} splits it from here.
     *
     * @param source          read-only input image
     * @param target          shared output image; this task writes only inside {@code tile}
     * @param kernel          stateless pixel transform
     * @param tile            region owned by this task
     * @param thresholdPixels leaf size; clamped to at least {@link #MIN_USEFUL_THRESHOLD_PIXELS}
     * @param token           cooperative cancellation, shared across the whole batch
     */
    public TileProcessingAction(BufferedImage source, BufferedImage target, TileKernel kernel,
            Tile tile, long thresholdPixels, CancellationToken token) {
        this.source = Preconditions.requireNonNull(source, "source");
        this.target = Preconditions.requireNonNull(target, "target");
        this.kernel = Preconditions.requireNonNull(kernel, "kernel");
        this.tile = Preconditions.requireNonNull(tile, "tile");
        this.thresholdPixels = Math.max(MIN_USEFUL_THRESHOLD_PIXELS, thresholdPixels);
        this.token = token == null ? CancellationToken.NONE : token;
    }

    /** Convenience factory covering an entire image. */
    public static TileProcessingAction forWholeImage(BufferedImage source, BufferedImage target,
            TileKernel kernel, long thresholdPixels, CancellationToken token) {
        return new TileProcessingAction(source, target, kernel,
                Tile.whole(source.getWidth(), source.getHeight()), thresholdPixels, token);
    }

    @Override
    protected void compute() {
        // Poll once per task, not per pixel: cheap here, ruinous in the inner loop.
        token.throwIfCancelled();

        if (isLeaf()) {
            TileProcessingEvent event = new TileProcessingEvent();
            event.begin();
            kernel.apply(source, target, tile);
            event.end();
            if (event.shouldCommit()) {
                event.kernelName = kernel.name();
                event.tileWidth = tile.width();
                event.tileHeight = tile.height();
                event.pixelCount = tile.pixelCount();
                event.commit();
            }
            return;
        }

        Tile[] halves = tile.split();
        invokeAll(
                new TileProcessingAction(source, target, kernel, halves[0], thresholdPixels, token),
                new TileProcessingAction(source, target, kernel, halves[1], thresholdPixels, token));
    }

    /**
     * A tile is a leaf when it is small enough, when it can no longer be halved (1&times;1) — the
     * condition that guarantees termination for any image shape — or when
     * {@link AdaptiveThrottle} reports heap pressure high enough that adding more concurrently-live
     * tile-sized scratch buffers would be counterproductive.
     */
    private boolean isLeaf() {
        return tile.pixelCount() <= thresholdPixels || !tile.splittable()
                || AdaptiveThrottle.shouldThrottleSplitting();
    }

    /** Exposed for tests asserting split behaviour without running the pool. */
    public Tile tile() {
        return tile;
    }

    @Override
    public String toString() {
        return "TileProcessingAction[" + kernel.name() + " " + tile + "]";
    }

    /**
     * {@code RecursiveAction} inherits {@code Serializable} from {@code ForkJoinTask}, but every
     * collaborator here is {@code transient} and this tree is never meant to cross that boundary
     * (see the class javadoc: "Not reusable... construct a fresh tree per image per operation").
     * Refusing outright beats silently handing back a task that NPEs the moment {@link #compute()}
     * touches {@code source}, {@code target}, {@code kernel}, or {@code tile}.
     */
    private void writeObject(ObjectOutputStream out) throws NotSerializableException {
        throw new NotSerializableException(getClass().getName());
    }

    private void readObject(ObjectInputStream in) throws NotSerializableException {
        throw new NotSerializableException(getClass().getName());
    }
}
