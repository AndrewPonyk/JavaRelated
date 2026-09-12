package com.parallelimage.core.io;

import com.parallelimage.core.error.ImageIoException;
import com.parallelimage.core.model.ProcessingOptions;
import com.parallelimage.core.util.Preconditions;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

/**
 * Encodes a processed image to disk.
 *
 * <h2>Write to a temp file, then move atomically</h2>
 * The naive version writes straight to {@code out/photo.jpg}. Kill the process (or the batch, or the
 * power) halfway through and the user is left with a file that <em>exists</em>, has a plausible size,
 * and is a truncated JPEG. Worse, a re-run with "skip existing" enabled will skip it forever. Writing
 * to {@code photo.jpg.<n>.tmp} and finishing with an {@link StandardCopyOption#ATOMIC_MOVE} means the
 * destination path only ever contains a complete file — the same discipline a database uses for its
 * data files. When the filesystem cannot do an atomic move (rare, but possible across mount points on
 * Windows) we fall back to a plain replace and log it.
 *
 * <h2>Alpha and JPEG</h2>
 * JPEG has no alpha channel. Handing {@code TYPE_INT_ARGB} to the JPEG writer produces either an
 * exception or — in older JDKs — a pink-tinted image, because the writer interprets the four bands as
 * CMYK. Transparency is therefore flattened onto an explicit white background first. Choosing white
 * rather than black is a product decision: a transparent PNG logo converted to JPEG almost always
 * wants a white page behind it (TECH-NOTES §3.6 E5).
 *
 * <h2>Thread-safety</h2>
 * As with {@link ImageLoader}: {@link ImageWriter} instances are not thread-safe, so one is obtained
 * and disposed per call. The class itself is stateless.
 */
public final class ImageSink {

    private static final Logger LOG = System.getLogger(ImageSink.class.getName());

    /** Formats whose writers accept an alpha channel. Everything else gets flattened. */
    private static final Set<String> ALPHA_CAPABLE = Set.of("png", "gif", "tiff", "bmp");

    private ImageSink() {
        throw new AssertionError("no instances");
    }

    /**
     * Writes {@code image} to {@code target}.
     *
     * @return the path actually written
     * @throws ImageIoException if the format has no writer, the target exists and overwriting is
     *                          disabled, or the write fails
     */
    public static Path write(BufferedImage image, Path target, ProcessingOptions options) {
        Preconditions.requireNonNull(image, "image");
        Preconditions.requireNonNull(target, "target");
        Preconditions.requireNonNull(options, "options");

        String format = options.outputFormat().toLowerCase(Locale.ROOT);
        if (Files.exists(target) && !options.overwriteExisting()) {
            throw new ImageIoException(target, "target already exists and overwrite is disabled");
        }

        Path targetName = target.getFileName();
        if (targetName == null) {
            throw new ImageIoException(target, "target has no file name component");
        }

        BufferedImage encodable = ALPHA_CAPABLE.contains(format) ? image : flatten(image);
        Path tempFile = null;
        try {
            Path parent = target.getParent();
            if (parent != null) {
                // Idempotent and safe to call concurrently: createDirectories tolerates an existing
                // directory, so no check-then-act race between workers writing to the same folder.
                Files.createDirectories(parent);
            }
            tempFile = Files.createTempFile(
                    parent == null ? Path.of(".") : parent, targetName.toString(), ".tmp");

            encode(encodable, tempFile, format, options);
            return moveIntoPlace(tempFile, target);
        } catch (IOException e) {
            throw new ImageIoException(target, "failed to write image", e);
        } finally {
            deleteQuietly(tempFile, target);
        }
    }

    private static void encode(BufferedImage image, Path file, String format,
            ProcessingOptions options) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(format);
        if (!writers.hasNext()) {
            throw new ImageIoException(file, "no ImageIO writer for format '" + format + "'");
        }
        ImageWriter writer = writers.next();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(file.toFile())) {
            if (out == null) {
                throw new ImageIoException(file, "could not open an image output stream");
            }
            writer.setOutput(out);

            ImageWriteParam param = writer.getDefaultWriteParam();
            if (options.isLossy() && param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                // getCompressionTypes() may be null for writers with a single implicit type; setting
                // a type in that case throws UnsupportedOperationException.
                String[] types = param.getCompressionTypes();
                if (types != null && types.length > 0 && param.getCompressionType() == null) {
                    param.setCompressionType(types[0]);
                }
                param.setCompressionQuality(options.quality());
            }
            writer.write(null, new IIOImage(image, null, null), param);
            // Flush before the stream closes so the ATOMIC_MOVE below cannot publish a partial file.
            out.flush();
        } finally {
            writer.dispose();
        }
    }

    private static Path moveIntoPlace(Path tempFile, Path target) throws IOException {
        try {
            return Files.move(tempFile, target,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            LOG.log(Level.DEBUG, () -> "atomic move unavailable for " + target
                    + "; falling back to a replacing move");
            return Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Composites onto opaque white and drops the alpha band.
     *
     * <p>Returns the argument unchanged when it has no alpha, so the common JPEG-to-JPEG path does not
     * pay for a full-image copy.
     */
    public static BufferedImage flatten(BufferedImage image) {
        if (!image.getColorModel().hasAlpha()) {
            return image;
        }
        BufferedImage opaque = new BufferedImage(
                image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = opaque.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, image.getWidth(), image.getHeight());
            g.drawImage(image, 0, 0, null);
        } finally {
            g.dispose();
        }
        return opaque;
    }

    /**
     * Removes the temp file if it is still there.
     *
     * <p>Called from a {@code finally}, so it must not throw: an exception here would replace the real
     * cause of the failure with a confusing "could not delete temp file", and the operator would spend
     * an afternoon debugging the wrong problem.
     */
    private static void deleteQuietly(Path tempFile, Path target) {
        if (tempFile == null) {
            return;
        }
        try {
            if (Files.deleteIfExists(tempFile)) {
                LOG.log(Level.DEBUG, () -> "cleaned up partial output for " + target.getFileName());
            }
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.DEBUG, "could not remove temp file", e);
        }
    }

    /**
     * Derives the output path for a source file under an output root, applying the target extension.
     *
     * @param relativeTo when non-{@code null}, the input root; the source's path relative to it is
     *                   preserved under {@code outputRoot} so a nested album structure survives
     */
    public static Path targetFor(Path source, Path outputRoot, Path relativeTo, String format) {
        Preconditions.requireNonNull(source, "source");
        Preconditions.requireNonNull(outputRoot, "outputRoot");

        Path relative = relativeTo == null ? source.getFileName() : relativeTo.relativize(source);
        Path relativeFileName = relative == null ? null : relative.getFileName();
        if (relativeFileName == null) {
            throw new ImageIoException(source, "source has no file name component");
        }
        String fileName = relativeFileName.toString();
        int dot = fileName.lastIndexOf('.');
        String stem = dot <= 0 ? fileName : fileName.substring(0, dot);

        Path parent = relative.getParent();
        Path directory = parent == null ? outputRoot : outputRoot.resolve(parent);
        return directory.resolve(stem + "." + format.toLowerCase(Locale.ROOT));
    }
}
