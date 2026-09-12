package com.parallelimage.core.io;

import com.parallelimage.core.error.ImageIoException;
import com.parallelimage.core.model.ImageJob;
import com.parallelimage.core.model.ProcessingOptions;
import com.parallelimage.core.util.Preconditions;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Walks a directory and turns it into a list of {@link ImageJob}s.
 *
 * <h2>Discovery is deliberately sequential</h2>
 * It is tempting to parallelize the walk. Don't: directory traversal is dominated by filesystem
 * metadata latency, not CPU, and the obvious way to parallelize it — {@code Files.walk(...)
 * .parallel()} — lands on {@code ForkJoinPool.commonPool()}, the pool this application never uses
 * (ARCHITECTURE §2.1, TECH-NOTES §3.6 A5). One thread listing files while N threads process them is
 * the right shape.
 *
 * <h2>Sorted output</h2>
 * Results are sorted by path. Filesystem enumeration order is unspecified and differs between NTFS
 * and ext4, which would make batch composition — and therefore every log, every progress sequence and
 * every regression test — irreproducible for no benefit.
 *
 * <h2>Symlinks are not followed</h2>
 * {@link Files#walk} without {@link FileVisitOption#FOLLOW_LINKS} cannot be trapped by a symlink cycle
 * and cannot be tricked into reading outside the requested tree. Both matter for a tool that users
 * will point at directories they did not curate.
 *
 * <p><strong>Stateless and thread-safe.</strong>
 */
public final class ImageDiscovery {

    /** Extensions ImageIO can read out of the box on every supported JDK. */
    public static final Set<String> DEFAULT_EXTENSIONS =
            Set.of("jpg", "jpeg", "png", "bmp", "gif", "tif", "tiff", "wbmp");

    /** Depth cap; a deeper tree than this is a mistake, or a symlink loop we did not follow. */
    private static final int MAX_DEPTH = 32;

    private ImageDiscovery() {
        throw new AssertionError("no instances");
    }

    /**
     * Finds candidate image files under {@code root}.
     *
     * @param root       file or directory to scan; a single file yields a single-element list
     * @param recursive  descend into subdirectories
     * @param extensions lower-case extensions without the dot; {@code null} → {@link #DEFAULT_EXTENSIONS}
     * @throws ImageIoException if {@code root} does not exist or cannot be walked
     */
    public static List<Path> find(Path root, boolean recursive, Set<String> extensions) {
        Preconditions.requireNonNull(root, "root");
        Set<String> accepted = extensions == null || extensions.isEmpty()
                ? DEFAULT_EXTENSIONS
                : extensions;

        if (!Files.exists(root)) {
            throw new ImageIoException(root, "input path does not exist");
        }
        if (Files.isRegularFile(root)) {
            return hasAcceptedExtension(root, accepted) ? List.of(root) : List.of();
        }

        // try-with-resources is mandatory: Files.walk holds an open directory stream per level, and
        // leaking them exhausts file handles a few thousand images into a batch.
        try (Stream<Path> walk = Files.walk(root, recursive ? MAX_DEPTH : 1)) {
            return walk.filter(Files::isRegularFile)
                    .filter(path -> hasAcceptedExtension(path, accepted))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        } catch (IOException e) {
            throw new ImageIoException(root, "failed to walk input directory", e);
        }
    }

    /**
     * Builds a complete batch: one {@link ImageJob} per discovered file, all sharing a batch id.
     *
     * <p>Output paths mirror the input tree beneath {@code outputRoot}, so processing
     * {@code photos/2024/rome/x.jpg} writes {@code out/2024/rome/x.jpg} rather than flattening
     * everything into one directory and silently overwriting same-named files from different albums.
     *
     * @return jobs in deterministic order; empty when nothing matched
     */
    public static List<ImageJob> plan(Path inputRoot, Path outputRoot, ProcessingOptions options,
            boolean recursive) {
        Preconditions.requireNonNull(inputRoot, "inputRoot");
        Preconditions.requireNonNull(outputRoot, "outputRoot");
        Preconditions.requireNonNull(options, "options");

        String batchId = UUID.randomUUID().toString();
        Path relativeTo = Files.isDirectory(inputRoot) ? inputRoot : null;

        return find(inputRoot, recursive, null).stream()
                .map(source -> ImageJob.create(
                        batchId,
                        source,
                        ImageSink.targetFor(source, outputRoot, relativeTo, options.outputFormat()),
                        options))
                .toList();
    }

    private static boolean hasAcceptedExtension(Path path, Set<String> accepted) {
        Path fileName = path.getFileName();
        if (fileName == null) {
            // Only a root path (e.g. "C:\") has no name element, and Files.isRegularFile is false
            // for those, so every real caller is already filtered -- this is a defensive fallback.
            return false;
        }
        String name = fileName.toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return false;
        }
        return accepted.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }
}
