package com.parallelimage.core.io;

import com.parallelimage.core.error.ImageIoException;
import com.parallelimage.core.filter.Pixels;
import com.parallelimage.core.model.ImageMetadata;
import com.parallelimage.core.util.Preconditions;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/**
 * Decodes an image file into a normalized {@link BufferedImage} plus its {@link ImageMetadata}.
 *
 * <h2>Header-first decoding, and why it is not optional</h2>
 * {@code ImageIO.read(file)} allocates the full raster before anyone can ask how big it is. A
 * 40&nbsp;KB PNG can legally declare 60000&times;60000 pixels — 14&nbsp;GB of {@code int[]} — and the
 * only symptom is an {@link OutOfMemoryError} that takes down whatever else the JVM was doing. Worse,
 * inside a fork/join pool that {@code OutOfMemoryError} surfaces on an arbitrary worker and can leave
 * a task tree half-joined. So this class opens a reader, asks for the dimensions from the
 * <em>header</em>, checks them against a budget, and only then decodes (TECH-NOTES §3.6 E7).
 *
 * <h2>ImageIO thread-safety</h2>
 * {@link ImageIO} the class is thread-safe; {@link ImageReader} instances emphatically are not, and
 * neither is the shared reader cache that {@code ImageIO.read} uses internally in some JDK builds.
 * Every method here therefore obtains its own reader and disposes of it in a {@code finally}. Sharing
 * a reader between workers produces corrupted output that looks like disk corruption
 * (TECH-NOTES §3.6 E1).
 *
 * <h2>Disk cache</h2>
 * {@link ImageIO#setUseCache(boolean)} is disabled once at class initialization. The default writes
 * temporary files under {@code java.io.tmpdir} for streams it cannot size, which on a batch of
 * thousands of images means thousands of temp files, contention on one directory, and I/O in the
 * middle of a CPU-bound pipeline.
 *
 * <p><strong>Stateless and thread-safe.</strong>
 */
public final class ImageLoader {

    private static final Logger LOG = System.getLogger(ImageLoader.class.getName());

    /**
     * Above this pixel count, a tiled source is decoded tile-by-tile rather than in one
     * {@code reader.read(0, param)} call, to avoid materialising the reader's own full-size
     * intermediate raster on top of the destination image during decode. Non-tiled formats
     * (JPEG/PNG) do not support {@link ImageReader#isImageTiled(int)} and are unaffected.
     */
    private static final long STREAMING_READ_THRESHOLD_PIXELS = 100_000_000L;

    static {
        // Global, process-wide, and correct for every use of ImageIO in this application.
        ImageIO.setUseCache(false);
    }

    private ImageLoader() {
        throw new AssertionError("no instances");
    }

    /** A decoded image together with the facts recorded about it. */
    public record Decoded(BufferedImage image, ImageMetadata metadata) { }

    /** Width, height and format read from the file header without decoding pixels. */
    public record Header(int width, int height, String formatName) {

        public long pixelCount() {
            return (long) width * height;
        }
    }

    /**
     * Reads dimensions and format from the header only.
     *
     * @throws ImageIoException if the file is unreadable or no registered reader recognises it
     */
    public static Header readHeader(Path source) {
        Preconditions.requireNonNull(source, "source");
        try (ImageInputStream stream = ImageIO.createImageInputStream(source.toFile())) {
            if (stream == null) {
                throw new ImageIoException(source, "no image input stream (unreadable file?)", null);
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                throw new ImageIoException(source, "no ImageIO reader for this format", null);
            }
            ImageReader reader = readers.next();
            try {
                // false, false: do not seek forward only, do not ignore metadata -- we want the
                // header cheaply and nothing else.
                reader.setInput(stream, true, true);
                return new Header(
                        reader.getWidth(0),
                        reader.getHeight(0),
                        reader.getFormatName().toLowerCase(Locale.ROOT));
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new ImageIoException(source, "failed to read image header", e);
        }
    }

    /**
     * Decodes {@code source} fully.
     *
     * @param jobId     correlation id stored in the returned metadata
     * @param source    file to decode
     * @param maxPixels refuse anything larger; see the class javadoc on decompression bombs
     * @throws ImageIoException on an unreadable file, an unsupported format, or a pixel budget breach
     */
    public static Decoded load(String jobId, Path source, long maxPixels) {
        Preconditions.requireNonBlank(jobId, "jobId");
        Preconditions.requireNonNull(source, "source");

        long sourceBytes = sizeOf(source);
        Header header = readHeader(source);
        if (maxPixels > 0 && header.pixelCount() > maxPixels) {
            throw new ImageIoException(source,
                    "image declares %d pixels (%dx%d), above the %d pixel limit".formatted(
                            header.pixelCount(), header.width(), header.height(), maxPixels),
                    null);
        }

        try (ImageInputStream stream = ImageIO.createImageInputStream(source.toFile())) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                throw new ImageIoException(source, "no ImageIO reader for this format", null);
            }
            ImageReader reader = readers.next();
            Map<String, String> exif;
            BufferedImage decoded;
            try {
                reader.setInput(stream, true, true);
                exif = readTextMetadata(reader);
                if (reader.isImageTiled(0) && header.pixelCount() > STREAMING_READ_THRESHOLD_PIXELS) {
                    decoded = readByTile(reader, header);
                } else {
                    ImageReadParam param = reader.getDefaultReadParam();
                    decoded = reader.read(0, param);
                }
            } finally {
                reader.dispose();
            }

            if (decoded == null) {
                throw new ImageIoException(source, "reader returned no image", null);
            }

            // Normalize once, here, so that no downstream kernel has to cope with indexed or
            // byte-interleaved rasters.
            BufferedImage normalized = Pixels.normalize(decoded);
            ImageMetadata metadata = new ImageMetadata(
                    jobId,
                    normalized.getWidth(),
                    normalized.getHeight(),
                    header.formatName(),
                    sourceBytes,
                    normalized.getType(),
                    normalized.getColorModel().hasAlpha(),
                    exif);
            return new Decoded(normalized, metadata);
        } catch (IOException e) {
            throw new ImageIoException(source, "failed to decode image", e);
        } catch (OutOfMemoryError e) {
            // The budget check above should have prevented this; if it did not, the file lied about
            // its header or the heap is simply too small for the configured parallelism.
            throw new ImageIoException(source, "out of memory decoding "
                    + header.width() + "x" + header.height() + " image", e);
        }
    }

    /**
     * Decodes a tiled source tile-by-tile into a pre-sized destination image, instead of one full
     * {@code reader.read(0, param)} call. For a source this large, the reader's own full-size
     * intermediate raster would otherwise be live in memory at the same time as the destination
     * image; reading one tile at a time keeps only one tile's worth of decoder-side memory alive at
     * once. Only called for sources where {@code reader.isImageTiled(0)} is {@code true}.
     */
    private static BufferedImage readByTile(ImageReader reader, Header header) throws IOException {
        BufferedImage destination =
                new BufferedImage(header.width(), header.height(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = destination.createGraphics();
        try {
            int tileWidth = reader.getTileWidth(0);
            int tileHeight = reader.getTileHeight(0);
            int numXTiles = (header.width() + tileWidth - 1) / tileWidth;
            int numYTiles = (header.height() + tileHeight - 1) / tileHeight;
            for (int ty = 0; ty < numYTiles; ty++) {
                for (int tx = 0; tx < numXTiles; tx++) {
                    BufferedImage tile = reader.readTile(0, tx, ty);
                    g.drawImage(tile, tx * tileWidth, ty * tileHeight, null);
                }
            }
        } finally {
            g.dispose();
        }
        return destination;
    }

    /**
     * Best-effort extraction of textual metadata (EXIF-ish key/values).
     *
     * <p>Best-effort on purpose. Metadata trees vary per format and per JDK vendor, and a malformed
     * EXIF block is not a reason to reject an otherwise perfectly good photograph. Failures are logged
     * at {@code DEBUG} and produce an empty map.
     *
     * <p>TODO(phase-3): walk the {@code javax_imageio_1.0} standard tree for
     * {@code Text/TextEntry} nodes and expose orientation so portrait JPEGs can be auto-rotated.
     */
    private static Map<String, String> readTextMetadata(ImageReader reader) {
        Map<String, String> result = new LinkedHashMap<>();
        try {
            result.put("imageio.format", reader.getFormatName());
            result.put("imageio.numImages", String.valueOf(reader.getNumImages(false)));
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.DEBUG, "could not read image metadata", e);
            return new HashMap<>();
        }
        return result;
    }

    private static long sizeOf(Path source) {
        try {
            return Files.size(source);
        } catch (IOException e) {
            throw new ImageIoException(source, "cannot stat file", e);
        } catch (UncheckedIOException e) {
            // The wrapper is kept as the cause rather than unwrapped to e.getCause(). Unwrapping
            // reads better in a one-line log, but it discards the frames that say *where* the
            // IOException was turned into an unchecked one — which for a filesystem provider that
            // wraps deep inside a stream is the only part of the trace pointing at our call.
            throw new ImageIoException(source, "cannot stat file", e);
        }
    }
}
