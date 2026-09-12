package com.parallelimage.nativebridge;

import com.parallelimage.core.filter.Pixels;
import com.parallelimage.core.pipeline.ImageOperation.EnhanceMode;
import com.parallelimage.core.spi.ImageEnhancer;
import java.awt.image.BufferedImage;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * {@link ImageEnhancer} backed by OpenCV through JNI.
 *
 * <p>Discovered by {@link java.util.ServiceLoader} via
 * {@code META-INF/services/com.parallelimage.core.spi.ImageEnhancer}. When the shared library is
 * missing, {@link #isAvailable()} returns {@code false}, {@code ImageEnhancer.discover()} skips this
 * provider, and {@code PassthroughEnhancer} runs instead — the user sees a warning, not a crash.
 *
 * <h2>What crosses the JNI boundary, and why it is a pair of direct {@link ByteBuffer}s</h2>
 * Not {@link BufferedImage}: reaching into a Java object's fields from C++ means
 * {@code GetFieldID}/{@code CallObjectMethod} chains that are slow, fragile across JDK versions, and
 * a segfault away from taking the JVM down. A direct buffer is the one shape whose address is stable
 * and whose lifetime is unambiguous: {@code GetDirectBufferAddress} hands C++ a raw pointer into
 * memory the JVM never moves, with no {@code GetPrimitiveArrayCritical}-style ban on JNI calls or
 * allocations for as long as it is held. So the contract is deliberately dull: packed little-endian
 * ARGB, row-major, exactly {@code width * height * 4} bytes, no stride, no offset — one buffer the
 * callee only reads, one it only writes.
 *
 * <p>Unlike a heap {@code int[]}, a direct buffer cannot alias {@link BufferedImage}'s backing
 * array, so {@link #compactArgb} now copies unconditionally rather than only when the raster has
 * padding. That is the same per-pixel copy this class already made in the padded case and already
 * measured at under 2% of the decode a 24-megapixel JPEG requires; making it unconditional does not
 * change that order of magnitude. What it buys back is a boundary the GC is never paused for.
 *
 * <h2>Mode codes are explicit, not ordinals</h2>
 * {@link #codeFor} maps each {@link EnhanceMode} to a hand-written constant. Passing
 * {@code mode.ordinal()} would work today and break silently the moment someone inserts a value into
 * the middle of the enum: the Java side would ask for {@code DENOISE} and the C++ side would run
 * super-resolution. An enum reordering is a refactor; it must not be able to change what the native
 * code computes. Adding a mode without a code here fails the {@code switch} at compile time, because
 * the enum is exhaustively matched with no {@code default}.
 *
 * <h2>Thread safety</h2>
 * Stateless apart from the one-shot model load, so all workers share one instance. The native side
 * must be re-entrant; see {@code native/README.md}.
 */
public final class NativeImageEnhancer implements ImageEnhancer {

    private static final Logger LOG = System.getLogger(NativeImageEnhancer.class.getName());

    /**
     * Bumped whenever the native signatures or the mode codes change.
     *
     * <p>Checked against {@link #nativeApiVersion()} at startup. Without this, a stale
     * {@code pip_enhance.dll} left in {@code PATH} from an older install links successfully — the
     * mangled names still match — and then misreads its arguments. A version handshake turns that
     * into a clear refusal at startup instead of corrupted output or a crash halfway through a batch.
     */
    private static final int API_VERSION = 1;

    // Stable wire values. Never renumber; append.
    private static final int CODE_CLAHE = 1;
    private static final int CODE_DENOISE = 2;
    private static final int CODE_SUPER_RESOLUTION = 3;

    /** Bytes per pixel in the wire format: one packed little-endian ARGB int. */
    private static final int BYTES_PER_PIXEL = Integer.BYTES;

    /** Set once the super-resolution model has been loaded (or has definitively failed to load). */
    private static final AtomicBoolean MODEL_ATTEMPTED = new AtomicBoolean(false);
    private static volatile boolean modelReady;

    /** Required by {@link java.util.ServiceLoader}. */
    public NativeImageEnhancer() {
    }

    @Override
    public boolean isAvailable() {
        if (!NativeLibraryLoader.isLoaded()) {
            return false;
        }
        try {
            int actual = nativeApiVersion();
            if (actual != API_VERSION) {
                LOG.log(Level.WARNING, () -> "native enhancer speaks API version " + actual
                        + " but this build expects " + API_VERSION
                        + "; ignoring it. Rebuild native/ or remove the stale library from PATH.");
                return false;
            }
            return true;
        } catch (Throwable t) {
            // The library loaded but the symbol is not there: an older build, or one compiled without
            // this entry point. Catching Throwable is deliberate -- UnsatisfiedLinkError is an Error.
            LOG.log(Level.WARNING, () -> "native enhancer failed its version handshake: " + t);
            return false;
        }
    }

    @Override
    public String describe() {
        if (!NativeLibraryLoader.isLoaded()) {
            return "OpenCV/JNI (unavailable: " + NativeLibraryLoader.failure() + ")";
        }
        try {
            return "OpenCV/JNI " + nativeDescribe() + " from " + NativeLibraryLoader.source();
        } catch (Throwable t) {
            return "OpenCV/JNI (loaded from " + NativeLibraryLoader.source() + ", version unknown)";
        }
    }

    /** Above {@code PassthroughEnhancer}'s {@link Integer#MIN_VALUE}, below any future provider. */
    @Override
    public int priority() {
        return 100;
    }

    @Override
    public boolean supports(EnhanceMode mode) {
        if (!NativeLibraryLoader.isLoaded()) {
            return false;
        }
        try {
            return nativeSupports(codeFor(mode));
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns {@code source} unchanged on any failure. A batch of 4 000 photographs must not stop
     * because the denoiser disliked one of them, and this is the layer that knows the difference
     * between "enhancement did nothing" (fine, the image is still correct) and a real error.
     */
    @Override
    public BufferedImage enhance(BufferedImage source, EnhanceMode mode, double strength) {
        if (source == null || !NativeLibraryLoader.isLoaded() || strength <= 0.0d) {
            return source;
        }
        if (mode == EnhanceMode.SUPER_RESOLUTION && !ensureModelLoaded()) {
            return source;
        }

        BufferedImage normalized = Pixels.normalize(source);
        int width = normalized.getWidth();
        int height = normalized.getHeight();
        ByteBuffer in = compactArgb(normalized);
        ByteBuffer out = ByteBuffer.allocateDirect(width * height * BYTES_PER_PIXEL)
                .order(ByteOrder.LITTLE_ENDIAN);

        ByteBuffer written;
        try {
            written = nativeEnhance(in, out, width, height, codeFor(mode), strength);
        } catch (Throwable t) {
            // Includes UnsatisfiedLinkError (missing symbol) and anything the C++ side chose to throw
            // back. What it cannot include is a segfault, which is why native/README.md insists the
            // C++ side validates its own bounds rather than trusting these arguments.
            LOG.log(Level.WARNING, () -> "native " + mode + " failed; using the unenhanced image: " + t);
            return source;
        }

        if (written == null) {
            // The documented way for the native side to say "not implemented here". Not an error:
            // OperationPipeline is allowed to receive the source back unchanged. A geometry change
            // (the callee wanting to return a different size) is not representable at all now that
            // `out` is pre-sized by this method -- there is nothing to discard a mismatched length
            // from, because the callee can only write into the buffer it was given.
            LOG.log(Level.DEBUG, () -> "native side declined " + mode + "; passing through");
            return source;
        }

        BufferedImage result = Pixels.sameShape(normalized);
        int[] destination = Pixels.data(result);
        int stride = Pixels.stride(result);
        int offset = Pixels.offset(result);
        IntBuffer written32 = written.asIntBuffer();
        for (int y = 0; y < height; y++) {
            written32.get(y * width, destination, offset + y * stride, width);
        }
        return result;
    }

    // ------------------------------------------------------------------------
    //  Marshalling
    // ------------------------------------------------------------------------

    /**
     * Returns the image's pixels as a tightly packed, little-endian direct {@link ByteBuffer} of
     * exactly {@code width * height * 4} bytes.
     *
     * <p>Always copies, row by row, from the raster's backing {@code int[]} — a direct buffer's
     * memory is off-heap and a heap array can never alias it, so unlike the pre-{@link ByteBuffer}
     * version of this method there is no fast path for a raster with no padding. A raster obtained
     * from {@link BufferedImage#getSubimage} has both a non-zero offset and a stride wider than the
     * image; handing that array to C++ as if it were compact would read a diagonal smear of the
     * parent image, which is why the copy always goes row by row rather than in one bulk transfer.
     */
    private static ByteBuffer compactArgb(BufferedImage image) {
        int[] data = Pixels.data(image);
        int stride = Pixels.stride(image);
        int offset = Pixels.offset(image);
        int width = image.getWidth();
        int height = image.getHeight();

        ByteBuffer buffer = ByteBuffer.allocateDirect(width * height * BYTES_PER_PIXEL)
                .order(ByteOrder.LITTLE_ENDIAN);
        IntBuffer ints = buffer.asIntBuffer();
        for (int y = 0; y < height; y++) {
            ints.put(y * width, data, offset + y * stride, width);
        }
        return buffer;
    }

    /**
     * Exhaustive by construction — no {@code default}, so a new {@link EnhanceMode} is a compile
     * error here rather than a runtime surprise in C++.
     */
    private static int codeFor(EnhanceMode mode) {
        return switch (mode) {
            case CLAHE -> CODE_CLAHE;
            case DENOISE -> CODE_DENOISE;
            case SUPER_RESOLUTION -> CODE_SUPER_RESOLUTION;
        };
    }

    // ------------------------------------------------------------------------
    //  One-shot model load
    // ------------------------------------------------------------------------

    /**
     * Loads the super-resolution model on first use, at most once per JVM.
     *
     * <h2>Why this one call uses {@link ForkJoinPool.ManagedBlocker} and the pixel work does not</h2>
     * Reading a ~50&nbsp;MB DNN model off disk is I/O: the worker thread that lost the race to
     * {@link #MODEL_ATTEMPTED} sits on a monitor doing nothing while the winner waits on the
     * filesystem. That is exactly the situation {@code ManagedBlocker} exists for — it tells the pool
     * "this thread is about to stop being useful", and the pool starts a compensating thread so
     * parallelism is maintained instead of collapsing to N-1 for the duration.
     *
     * <p>The per-image OpenCV calls in {@link #enhance} pointedly do <em>not</em> do this, and
     * wrapping them would be a performance bug rather than a fix. They are CPU-bound: the thread is
     * saturating a core, not waiting. Compensating for it would start extra threads to contend for
     * cores that are already busy, and the batch would get slower while looking, in a thread dump,
     * like it had more parallelism. {@code ManagedBlocker} is for threads that are <em>idle</em>,
     * which is not the same as threads that are <em>slow</em>.
     *
     * @return whether the model is usable
     */
    private static boolean ensureModelLoaded() {
        if (MODEL_ATTEMPTED.get()) {
            return modelReady;
        }
        try {
            ForkJoinPool.managedBlock(new ModelLoad());
        } catch (InterruptedException e) {
            // Cancellation arrived while we waited. Restore the flag the engine's CancellationToken
            // checks and skip enhancement; the batch is being torn down anyway.
            Thread.currentThread().interrupt();
            return false;
        }
        return modelReady;
    }

    /**
     * The load itself, expressed as a {@link ForkJoinPool.ManagedBlocker}.
     *
     * <p>{@code isReleasable}/{@code block} is a slightly awkward shape for "do this once", but it is
     * the only way to hand the pool the information it needs. Outside a fork/join worker
     * {@code managedBlock} simply calls {@code block()} directly, so the CLI path works too.
     */
    private static final class ModelLoad implements ForkJoinPool.ManagedBlocker {

        @Override
        public boolean isReleasable() {
            return MODEL_ATTEMPTED.get();
        }

        @Override
        public boolean block() {
            // compareAndSet, not a plain check: several workers reach a SUPER_RESOLUTION stage at the
            // same instant, and loading a model three times concurrently is how you turn a 50 MB read
            // into a 150 MB read and an OutOfMemoryError.
            if (MODEL_ATTEMPTED.compareAndSet(false, true)) {
                try {
                    String path = System.getProperty("pip.native.model", "");
                    modelReady = nativeLoadModel(path.isBlank() ? null : path);
                    if (!modelReady) {
                        LOG.log(Level.WARNING, () -> "super-resolution model not loaded"
                                + (path.isBlank()
                                        ? "; set -Dpip.native.model=/path/to/model.pb"
                                        : " from " + path)
                                + "; SUPER_RESOLUTION will pass images through unchanged");
                    }
                } catch (Throwable t) {
                    modelReady = false;
                    LOG.log(Level.WARNING, () -> "super-resolution model load failed: " + t);
                }
            }
            return true;
        }
    }

    // ------------------------------------------------------------------------
    //  Native entry points
    //
    //  Signatures here define native/include/com_parallelimage_nativebridge_NativeImageEnhancer.h,
    //  which javac regenerates on every build (see the -h flag in pip-native/pom.xml). Do not hand-edit
    //  that header: changing a parameter type here and the header there is how the mangled names stop
    //  matching and every call becomes an UnsatisfiedLinkError with no hint as to why.
    // ------------------------------------------------------------------------

    /** @return the ABI version the loaded library implements; compared against {@link #API_VERSION} */
    private static native int nativeApiVersion();

    /** @return OpenCV build string, e.g. {@code "4.10.0"}; used only in log and status text */
    private static native String nativeDescribe();

    /** @param modeCode one of the {@code CODE_*} constants */
    private static native boolean nativeSupports(int modeCode);

    /**
     * @param modelPath absolute path to the DNN model, or {@code null} to use the built-in default
     * @return whether the model is ready for use
     */
    private static native boolean nativeLoadModel(String modelPath);

    /**
     * Runs the enhancement.
     *
     * <p>Both buffers are little-endian packed ARGB, row-major, exactly {@code width * height * 4}
     * bytes, and must be direct — see {@code native/README.md}'s data contract.
     *
     * @param argb the source pixels; must not be mutated by the callee — the engine may still hold
     *     the source image
     * @param out where the result is written on success; may be left untouched otherwise
     * @param modeCode one of the {@code CODE_*} constants
     * @param strength 0.0..1.0
     * @return {@code out} itself if the callee wrote a result into it, or {@code null} for
     *     "this mode is not implemented" (or any internal failure)
     */
    private static native ByteBuffer nativeEnhance(
            ByteBuffer argb, ByteBuffer out, int width, int height, int modeCode, double strength);
}
