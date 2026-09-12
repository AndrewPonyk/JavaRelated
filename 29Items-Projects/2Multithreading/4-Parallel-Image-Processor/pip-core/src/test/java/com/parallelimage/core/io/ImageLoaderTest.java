package com.parallelimage.core.io;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.parallelimage.core.error.ImageIoException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.metadata.IIOMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link ImageLoader} tests. {@code ImageProcessingEngineTest} pins the end-to-end decode path;
 * these pin the internal boundaries and edge cases that black-box, whole-batch assertions cannot
 * see: exact decode-bomb boundary comparisons, the metadata returned on both the happy and
 * exception paths of header/text-metadata reading, the reported file size, and the tile-assembly
 * arithmetic used for the streaming (tiled) decode path.
 *
 * <p>Real tiled formats (e.g. TIFF) are not registered with {@code ImageIO} in this JDK and adding
 * one would violate {@code pip-core}'s "JDK only" dependency rule (see {@code pom.xml}), so
 * {@code readByTile} is exercised directly, through reflection, against a minimal hand-written
 * {@link ImageReader} stub that reports itself as tiled. That is the only way to reach that method
 * without allocating a real >100,000,000-pixel raster.
 */
class ImageLoaderTest {

    @TempDir
    private Path temp;

    @Test
    @DisplayName("readHeader reports width, height and a lower-cased format name without decoding pixels")
    void readHeaderReportsDimensionsAndFormat() throws IOException {
        Path file = writePng(64, 48, false);

        ImageLoader.Header header = ImageLoader.readHeader(file);

        assertEquals(64, header.width());
        assertEquals(48, header.height());
        assertEquals("png", header.formatName());
        assertEquals(64L * 48L, header.pixelCount());
    }

    @Test
    @DisplayName("readHeader throws ImageIoException when no registered reader recognises the file")
    void readHeaderThrowsForUnrecognizedFormat() throws IOException {
        Path file = temp.resolve("not-an-image.bin");
        Files.writeString(file, "definitely not a picture");

        ImageIoException thrown = assertThrows(ImageIoException.class, () -> ImageLoader.readHeader(file));
        assertTrue(thrown.getMessage().contains("no ImageIO reader"), thrown.getMessage());
    }

    @Test
    @DisplayName("load decodes pixels and records width, height, format and alpha in the metadata")
    void loadDecodesAndPopulatesMetadata() throws IOException {
        Path file = writePng(20, 10, true);

        ImageLoader.Decoded decoded = ImageLoader.load("job-1", file, 0);

        assertEquals(20, decoded.image().getWidth());
        assertEquals(10, decoded.image().getHeight());
        assertEquals("job-1", decoded.metadata().jobId());
        assertEquals(20, decoded.metadata().width());
        assertEquals(10, decoded.metadata().height());
        assertEquals("png", decoded.metadata().formatName());
        assertTrue(decoded.metadata().hasAlpha());
    }

    @Test
    @DisplayName("load populates the exif map from the reader's own format name and image count")
    void loadPopulatesExifTextMetadata() throws IOException {
        Path file = writePng(8, 8, false);

        ImageLoader.Decoded decoded = ImageLoader.load("job-exif", file, 0);

        Map<String, String> exif = decoded.metadata().exif();
        assertEquals(2, exif.size(), "an empty map here means the reader's happy-path metadata was lost: " + exif);
        assertTrue(exif.containsKey("imageio.format"));
        assertEquals("1", exif.get("imageio.numImages"));
    }

    @Test
    @DisplayName("load reports the real on-disk file size, not a stubbed-out zero")
    void loadReportsActualSourceFileSize() throws IOException {
        Path file = writePng(30, 30, false);
        long actualSize = Files.size(file);

        ImageLoader.Decoded decoded = ImageLoader.load("job-size", file, 0);

        assertTrue(actualSize > 0, "the fixture itself must not be empty");
        assertEquals(actualSize, decoded.metadata().sourceBytes());
    }

    @Test
    @DisplayName("maxPixels=0 disables the decode-bomb guard entirely")
    void maxPixelsZeroDisablesTheGuard() throws IOException {
        Path file = writePng(50, 50, false);

        assertDoesNotThrow(() -> ImageLoader.load("job-unbounded", file, 0));
    }

    @Test
    @DisplayName("an image whose pixel count exactly equals the limit is accepted, not rejected")
    void imageExactlyAtThePixelLimitIsAccepted() throws IOException {
        Path file = writePng(10, 10, false);

        assertDoesNotThrow(() -> ImageLoader.load("job-at-limit", file, 100L));
    }

    @Test
    @DisplayName("an image one pixel over the limit is rejected with a message naming the breach")
    void imageOneOverThePixelLimitIsRejected() throws IOException {
        Path file = writePng(10, 10, false);

        ImageIoException thrown = assertThrows(ImageIoException.class,
                () -> ImageLoader.load("job-over-limit", file, 99L));
        assertTrue(thrown.getMessage().contains("pixel limit"), thrown.getMessage());
    }

    @Test
    @DisplayName("load throws ImageIoException for a file no reader recognises")
    void loadThrowsForUnreadableFile() throws IOException {
        Path file = temp.resolve("corrupt.png");
        Files.writeString(file, "not a png");

        assertThrows(ImageIoException.class, () -> ImageLoader.load("job-corrupt", file, 0));
    }

    @Test
    @DisplayName("readTextMetadata returns a mutable, empty map when the reader throws")
    void readTextMetadataReturnsMutableEmptyMapOnFailure() throws Exception {
        Map<String, String> result = invokeReadTextMetadata(new ThrowingFormatNameReader());

        assertTrue(result.isEmpty());
        assertDoesNotThrow(() -> result.put("probe", "value"),
                "the catch branch must return a mutable map, not an immutable Collections.emptyMap()");
    }

    @Test
    @DisplayName("readByTile assembles a full-size image from a partial trailing row and column of tiles")
    void readByTileAssemblesAllTilesExactlyOnce() throws Exception {
        // Deliberately not a multiple of the tile size in either dimension, so every arithmetic
        // operator in the tile-count and placement formulas is exercised: (13+5-1)/5 = 3 tiles wide,
        // (9+4-1)/4 = 3 tiles tall, with a short last column and a short last row.
        int width = 13;
        int height = 9;
        int tileWidth = 5;
        int tileHeight = 4;
        RecordingTileReader reader = new RecordingTileReader(tileWidth, tileHeight);
        ImageLoader.Header header = new ImageLoader.Header(width, height, "test");

        BufferedImage assembled = invokeReadByTile(reader, header);

        assertNotNull(assembled, "readByTile must not return null");
        assertEquals(width, assembled.getWidth());
        assertEquals(height, assembled.getHeight());

        Set<Point> expectedCalls = new HashSet<>();
        for (int ty = 0; ty < 3; ty++) {
            for (int tx = 0; tx < 3; tx++) {
                expectedCalls.add(new Point(tx, ty));
            }
        }
        assertEquals(expectedCalls, new HashSet<>(reader.tileCalls),
                "expected exactly a 3x3 grid of tiles, no more, no fewer, no duplicates");
        assertEquals(9, reader.tileCalls.size());

        // Placement: the tile at grid (0,0) must land at pixel (0,0)...
        assertEquals(RecordingTileReader.colorFor(0, 0), assembled.getRGB(0, 0));
        // ...the tile at grid (2,0) at x = 2*5 = 10 (the short last column)...
        assertEquals(RecordingTileReader.colorFor(2, 0), assembled.getRGB(10, 0));
        // ...and the tile at grid (2,2) at y = 2*4 = 8 (the short last row).
        assertEquals(RecordingTileReader.colorFor(2, 2), assembled.getRGB(10, 8));
        assertEquals(RecordingTileReader.colorFor(0, 1), assembled.getRGB(0, 4));
    }

    // ---- helpers ------------------------------------------------------------------------------

    private Path writePng(int width, int height, boolean alpha) throws IOException {
        Path file = Files.createTempFile(temp, "loader-", ".png");
        BufferedImage image = new BufferedImage(width, height,
                alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        javax.imageio.ImageIO.write(image, "png", file.toFile());
        return file;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> invokeReadTextMetadata(ImageReader reader) throws Exception {
        Method method = ImageLoader.class.getDeclaredMethod("readTextMetadata", ImageReader.class);
        method.setAccessible(true);
        return (Map<String, String>) method.invoke(null, reader);
    }

    private static BufferedImage invokeReadByTile(ImageReader reader, ImageLoader.Header header) throws Exception {
        Method method = ImageLoader.class.getDeclaredMethod("readByTile", ImageReader.class, ImageLoader.Header.class);
        method.setAccessible(true);
        try {
            return (BufferedImage) method.invoke(null, reader, header);
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw e;
        }
    }

    /** Minimal {@link ImageReader} stub whose only interesting behaviour is a throwing format name. */
    private static final class ThrowingFormatNameReader extends ImageReader {
        ThrowingFormatNameReader() {
            super(null);
        }

        @Override
        public String getFormatName() throws IOException {
            throw new IOException("no format available");
        }

        @Override
        public int getNumImages(boolean allowSearch) {
            return 1;
        }

        @Override
        public int getWidth(int imageIndex) {
            return 1;
        }

        @Override
        public int getHeight(int imageIndex) {
            return 1;
        }

        @Override
        public Iterator<ImageTypeSpecifier> getImageTypes(int imageIndex) {
            return Collections.emptyIterator();
        }

        @Override
        public IIOMetadata getStreamMetadata() {
            return null;
        }

        @Override
        public IIOMetadata getImageMetadata(int imageIndex) {
            return null;
        }

        @Override
        public BufferedImage read(int imageIndex, ImageReadParam param) {
            throw new UnsupportedOperationException("not used by this test");
        }
    }

    /**
     * Minimal tiled {@link ImageReader} stub. Records every {@code readTile} call and returns a
     * small, uniquely-colored opaque tile so the caller (readByTile) can be checked for both which
     * tiles it fetched and where it placed them.
     */
    private static final class RecordingTileReader extends ImageReader {
        private final int tileWidth;
        private final int tileHeight;
        final List<Point> tileCalls = new ArrayList<>();

        RecordingTileReader(int tileWidth, int tileHeight) {
            super(null);
            this.tileWidth = tileWidth;
            this.tileHeight = tileHeight;
        }

        static int colorFor(int tileX, int tileY) {
            return 0xFF000000 | ((32 + tileX * 40) << 16) | ((32 + tileY * 40) << 8) | 0x20;
        }

        @Override
        public int getTileWidth(int imageIndex) {
            return tileWidth;
        }

        @Override
        public int getTileHeight(int imageIndex) {
            return tileHeight;
        }

        @Override
        public BufferedImage readTile(int imageIndex, int tileX, int tileY) {
            tileCalls.add(new Point(tileX, tileY));
            BufferedImage tile = new BufferedImage(tileWidth, tileHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = tile.createGraphics();
            try {
                g.setColor(new Color(colorFor(tileX, tileY), true));
                g.fillRect(0, 0, tileWidth, tileHeight);
            } finally {
                g.dispose();
            }
            return tile;
        }

        @Override
        public int getNumImages(boolean allowSearch) {
            return 1;
        }

        @Override
        public int getWidth(int imageIndex) {
            return 0;
        }

        @Override
        public int getHeight(int imageIndex) {
            return 0;
        }

        @Override
        public Iterator<ImageTypeSpecifier> getImageTypes(int imageIndex) {
            return Collections.emptyIterator();
        }

        @Override
        public IIOMetadata getStreamMetadata() {
            return null;
        }

        @Override
        public IIOMetadata getImageMetadata(int imageIndex) {
            return null;
        }

        @Override
        public BufferedImage read(int imageIndex, ImageReadParam param) {
            throw new UnsupportedOperationException("not used by this test");
        }
    }
}
