package com.parallelimage.core.io;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.parallelimage.core.error.ImageIoException;
import com.parallelimage.core.model.ProcessingOptions;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.spi.IIORegistry;
import javax.imageio.spi.ImageWriterSpi;
import javax.imageio.stream.ImageOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link ImageSink} tests. {@code ImageProcessingEngineTest} pins the end-to-end write path (a
 * batch really lands on disk, a JPEG really loses its alpha, no {@code .tmp} survives); these pin
 * the internal decisions that a whole-batch assertion cannot see: the exact extension/boundary
 * arithmetic in {@link ImageSink#targetFor}, the compression-parameter wiring inside the private
 * {@code encode} method, and the cleanup/return-value plumbing in {@code write}.
 *
 * <p>{@code encode}'s compression wiring is only reachable with a real, registered
 * {@link ImageWriter} whose {@link ImageWriteParam} answers exactly the way the test wants — real
 * JDK writer plugins are not under this module's control and their {@code ImageWriteParam}
 * behaviour is not part of any documented contract. Rather than depend on that, a minimal
 * hand-written writer/SPI pair is registered with {@link IIORegistry} under a private format name
 * for the duration of each test, and the private {@code encode} method is invoked directly through
 * reflection with that format name — decoupled from {@code options.outputFormat()}, which still
 * drives {@link ProcessingOptions#isLossy()} independently, exactly as the real method treats them.
 */
class ImageSinkTest {

    private static final String FORMAT_NAME = "pip-core-test-fake";

    @TempDir
    private Path temp;

    private FakeWriterSpi activeSpi;

    @AfterEach
    void deregisterFakeWriter() {
        if (activeSpi != null) {
            IIORegistry.getDefaultInstance().deregisterServiceProvider(activeSpi);
            activeSpi = null;
        }
    }

    // ---- write() --------------------------------------------------------------------------------

    @Test
    @DisplayName("write creates parent directories, encodes the image and returns moveIntoPlace's result unchanged")
    void writeCreatesFileAndReturnsTarget() throws IOException {
        Path target = temp.resolve("nested").resolve("out.png");
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        ProcessingOptions options = ProcessingOptions.builder().outputFormat("png").build();

        Path result = ImageSink.write(image, target, options);

        assertEquals(target, result, "a null or wrong return here means moveIntoPlace's result was discarded");
        assertTrue(Files.isRegularFile(target));
        assertNotNull(ImageIO.read(target.toFile()), "the written file must be a decodable PNG");
    }

    @Test
    @DisplayName("write refuses an existing target when overwriteExisting is disabled, and leaves it untouched")
    void writeRefusesExistingTargetByDefault() throws IOException {
        Path target = temp.resolve("out.png");
        Files.writeString(target, "already here");
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ProcessingOptions options = ProcessingOptions.builder().outputFormat("png").build();

        ImageIoException thrown = assertThrows(ImageIoException.class, () -> ImageSink.write(image, target, options));

        assertTrue(thrown.getMessage().contains("already exists"), thrown.getMessage());
        assertEquals("already here", Files.readString(target), "a refused write must not touch the existing file");
    }

    @Test
    @DisplayName("write replaces an existing target when overwriteExisting is enabled")
    void writeReplacesExistingTargetWhenAllowed() throws IOException {
        Path target = temp.resolve("out.png");
        Files.writeString(target, "stale placeholder");
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ProcessingOptions options = ProcessingOptions.builder().outputFormat("png").overwriteExisting(true).build();

        Path result = ImageSink.write(image, target, options);

        assertEquals(target, result);
        assertNotNull(ImageIO.read(target.toFile()), "the stale placeholder must have been replaced with a real image");
    }

    @Test
    @DisplayName("write throws when the target path has no file name component")
    void writeThrowsWhenTargetHasNoFileName() {
        Path root = temp.getRoot();
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ProcessingOptions options = ProcessingOptions.builder().outputFormat("png").overwriteExisting(true).build();

        ImageIoException thrown = assertThrows(ImageIoException.class, () -> ImageSink.write(image, root, options));

        assertTrue(thrown.getMessage().contains("no file name component"), thrown.getMessage());
    }

    @Test
    @DisplayName("write cleans up its temp file when encoding fails, leaving no .tmp litter")
    void writeCleansUpTempFileOnEncodeFailure() throws IOException {
        Path target = temp.resolve("out.bin");
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ProcessingOptions options = ProcessingOptions.builder().outputFormat("not-a-real-format").build();

        // encode() throws ImageIoException directly for a missing writer, which is not an IOException,
        // so write()'s catch block never wraps it as "failed to write image" -- the more specific
        // message propagates as-is. The finally block still cleans up the temp file either way.
        ImageIoException thrown = assertThrows(ImageIoException.class, () -> ImageSink.write(image, target, options));
        assertTrue(thrown.getMessage().contains("no ImageIO writer for format"), thrown.getMessage());

        try (Stream<Path> files = Files.list(temp)) {
            List<String> leftovers = files.map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".tmp"))
                    .toList();
            assertEquals(List.of(), leftovers, "a failed encode must not leave its temp file behind");
        }
    }

    // ---- encode() (via reflection, against a hand-written fake writer) --------------------------

    @Test
    @DisplayName("encode enables explicit compression, picks the sole compression type and applies the requested quality")
    void encodeWiresCompressionForALossyWriterThatSupportsIt() throws Exception {
        FakeWriteParam param = new FakeWriteParam(true, new String[] {"FAKE"}, null);
        FakeImageWriter writer = registerFakeWriter(param);
        ProcessingOptions options = ProcessingOptions.builder().outputFormat("jpg").quality(0.42f).build();

        invokeEncode(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), temp.resolve("a.bin"), FORMAT_NAME, options);

        assertTrue(param.compressionModeSet, "compression mode must be set for a lossy format whose writer supports it");
        assertEquals(ImageWriteParam.MODE_EXPLICIT, param.compressionModeValue);
        assertTrue(param.compressionTypeSet, "the sole available compression type must be selected automatically");
        assertEquals("FAKE", param.compressionType());
        assertTrue(param.compressionQualitySet);
        assertEquals(0.42f, param.compressionQualityValue, 1e-6f);
        assertTrue(writer.writeCalled);
        assertTrue(writer.disposed, "the writer must be disposed even after a successful encode");
    }

    @Test
    @DisplayName("encode never selects a compression type when the writer offers none")
    void encodeSkipsCompressionTypeWhenNoneOffered() throws Exception {
        FakeWriteParam param = new FakeWriteParam(true, null, null);
        registerFakeWriter(param);
        ProcessingOptions options = ProcessingOptions.builder().outputFormat("jpg").build();

        invokeEncode(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), temp.resolve("b.bin"), FORMAT_NAME, options);

        assertTrue(param.compressionModeSet);
        assertFalse(param.compressionTypeSet, "there is nothing to select from a null compression-types array");
        assertTrue(param.compressionQualitySet);
    }

    @Test
    @DisplayName("encode never selects a compression type from an empty compression-types array")
    void encodeSkipsCompressionTypeWhenArrayEmpty() throws Exception {
        FakeWriteParam param = new FakeWriteParam(true, new String[0], null);
        registerFakeWriter(param);
        ProcessingOptions options = ProcessingOptions.builder().outputFormat("jpg").build();

        invokeEncode(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), temp.resolve("c.bin"), FORMAT_NAME, options);

        assertTrue(param.compressionModeSet);
        assertFalse(param.compressionTypeSet, "length 0 must be treated the same as no types at all");
        assertTrue(param.compressionQualitySet);
    }

    @Test
    @DisplayName("encode leaves an already-selected compression type alone")
    void encodeDoesNotOverwriteAnAlreadySelectedCompressionType() throws Exception {
        FakeWriteParam param = new FakeWriteParam(true, new String[] {"FAKE"}, "already-set");
        registerFakeWriter(param);
        ProcessingOptions options = ProcessingOptions.builder().outputFormat("jpg").build();

        invokeEncode(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), temp.resolve("d.bin"), FORMAT_NAME, options);

        assertTrue(param.compressionModeSet);
        assertFalse(param.compressionTypeSet, "a non-null compression type must not be replaced");
        assertEquals("already-set", param.compressionType());
        assertTrue(param.compressionQualitySet);
    }

    @Test
    @DisplayName("encode leaves compression untouched for a writer that cannot write compressed data")
    void encodeSkipsCompressionWhenWriterCannot() throws Exception {
        FakeWriteParam param = new FakeWriteParam(false, new String[] {"FAKE"}, null);
        registerFakeWriter(param);
        ProcessingOptions options = ProcessingOptions.builder().outputFormat("jpg").build();

        invokeEncode(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), temp.resolve("e.bin"), FORMAT_NAME, options);

        assertFalse(param.compressionModeSet, "a writer that cannot write compressed data must not be told to");
        assertFalse(param.compressionTypeSet);
        assertFalse(param.compressionQualitySet);
    }

    @Test
    @DisplayName("encode leaves compression untouched for a lossless format even when the writer supports it")
    void encodeSkipsCompressionForALosslessFormat() throws Exception {
        FakeWriteParam param = new FakeWriteParam(true, new String[] {"FAKE"}, null);
        registerFakeWriter(param);
        ProcessingOptions options = ProcessingOptions.builder().outputFormat("png").build();

        invokeEncode(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), temp.resolve("f.bin"), FORMAT_NAME, options);

        assertFalse(param.compressionModeSet, "png is not lossy; compression parameters must be left alone");
        assertFalse(param.compressionTypeSet);
        assertFalse(param.compressionQualitySet);
    }

    // ---- flatten() --------------------------------------------------------------------------------

    @Test
    @DisplayName("flatten returns the same instance when the image already has no alpha channel")
    void flattenReturnsSameInstanceForOpaqueImages() {
        BufferedImage image = new BufferedImage(3, 3, BufferedImage.TYPE_INT_RGB);

        assertSame(image, ImageSink.flatten(image));
    }

    @Test
    @DisplayName("flatten composites a fully transparent pixel onto white, not an unpainted black canvas")
    void flattenCompositesTransparentPixelsOntoWhite() {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0x00000000);

        BufferedImage flattened = ImageSink.flatten(image);

        assertFalse(flattened.getColorModel().hasAlpha());
        assertEquals(0xFFFFFFFF, flattened.getRGB(0, 0),
                "removing setColor/fillRect would leave the default black raster showing through instead of white");
    }

    // ---- targetFor() ------------------------------------------------------------------------------

    @Test
    @DisplayName("relativeTo=null resolves the target directly under outputRoot using just the file name")
    void targetForWithNullRelativeToUsesFileNameOnly() {
        Path source = Path.of("some", "nested", "photo.png");

        Path result = ImageSink.targetFor(source, temp, null, "jpg");

        assertEquals(temp.resolve("photo.jpg"), result);
    }

    @Test
    @DisplayName("a nested source path is preserved under outputRoot when relativeTo is given")
    void targetForPreservesNestedStructure() {
        Path relativeTo = Path.of("input");
        Path source = relativeTo.resolve("2026").resolve("summer").resolve("beach.png");

        Path result = ImageSink.targetFor(source, temp, relativeTo, "png");

        assertEquals(temp.resolve("2026").resolve("summer").resolve("beach.png"), result);
    }

    @Test
    @DisplayName("targetFor only lower-cases the format; it does not translate 'jpeg' to 'jpg'")
    void targetForDoesNotNormalizeJpegToJpg() {
        Path source = Path.of("photo.png");

        Path result = ImageSink.targetFor(source, temp, null, "JPEG");

        assertEquals(temp.resolve("photo.jpeg"), result, "extension translation belongs to ProcessingOptions, not targetFor");
    }

    @Test
    @DisplayName("a filename whose only dot is a leading dot is treated as having no extension")
    void targetForTreatsLeadingDotAsNoExtension() {
        Path source = Path.of("album", ".hidden");

        Path result = ImageSink.targetFor(source, temp, null, "png");

        assertEquals(temp.resolve(".hidden.png"), result,
                "a boundary error here would strip the dotfile's whole name down to an empty stem");
    }

    @Test
    @DisplayName("a source with no file name component throws ImageIoException")
    void targetForThrowsWhenSourceHasNoFileName() {
        Path root = temp.getRoot();

        ImageIoException thrown = assertThrows(ImageIoException.class,
                () -> ImageSink.targetFor(root, temp, null, "png"));

        assertTrue(thrown.getMessage().contains("no file name component"), thrown.getMessage());
    }

    // ---- deleteQuietly() (private, via reflection) -------------------------------------------------

    @Test
    @DisplayName("deleteQuietly is a no-op, not a NullPointerException, when there is no temp file to clean up")
    void deleteQuietlyToleratesNullTempFile() throws Exception {
        assertDoesNotThrow(() -> invokeDeleteQuietly(null, temp.resolve("target.png")));
    }

    @Test
    @DisplayName("deleteQuietly swallows a failure to delete instead of propagating it")
    void deleteQuietlySwallowsDeletionFailure() throws Exception {
        Path directoryAsTempFile = Files.createDirectory(temp.resolve("not-empty"));
        Files.writeString(directoryAsTempFile.resolve("child.txt"), "content");

        assertDoesNotThrow(() -> invokeDeleteQuietly(directoryAsTempFile, temp.resolve("target.png")));
        assertTrue(Files.exists(directoryAsTempFile), "a non-empty directory cannot be deleted and must be left alone");
    }

    // ---- helpers ------------------------------------------------------------------------------

    private FakeImageWriter registerFakeWriter(FakeWriteParam param) {
        FakeImageWriter writer = new FakeImageWriter(param);
        activeSpi = new FakeWriterSpi(FORMAT_NAME, writer);
        IIORegistry.getDefaultInstance().registerServiceProvider(activeSpi);
        return writer;
    }

    private static void invokeEncode(BufferedImage image, Path file, String format, ProcessingOptions options)
            throws Exception {
        Method method = ImageSink.class.getDeclaredMethod(
                "encode", BufferedImage.class, Path.class, String.class, ProcessingOptions.class);
        method.setAccessible(true);
        try {
            method.invoke(null, image, file, format, options);
        } catch (InvocationTargetException e) {
            rethrow(e);
        }
    }

    private static void invokeDeleteQuietly(Path tempFile, Path target) throws Exception {
        Method method = ImageSink.class.getDeclaredMethod("deleteQuietly", Path.class, Path.class);
        method.setAccessible(true);
        try {
            method.invoke(null, tempFile, target);
        } catch (InvocationTargetException e) {
            rethrow(e);
        }
    }

    private static void rethrow(InvocationTargetException e) throws Exception {
        Throwable cause = e.getCause();
        if (cause instanceof Exception exception) {
            throw exception;
        }
        if (cause instanceof Error error) {
            throw error;
        }
        throw e;
    }

    /**
     * Hand-written {@link ImageWriteParam}: every method the production code calls is overridden to
     * simply record what happened, rather than relying on any particular real JDK plugin's
     * undocumented behaviour (e.g. whether {@code setCompressionMode} happens to reset an existing
     * compression type).
     */
    private static final class FakeWriteParam extends ImageWriteParam {
        boolean compressionModeSet;
        int compressionModeValue = MODE_DISABLED;
        boolean compressionTypeSet;
        boolean compressionQualitySet;
        float compressionQualityValue = Float.NaN;

        private final boolean canWriteCompressedValue;
        private final String[] compressionTypesValue;
        private String compressionTypeValue;

        FakeWriteParam(boolean canWriteCompressedValue, String[] compressionTypesValue, String initialCompressionType) {
            super(Locale.ROOT);
            this.canWriteCompressedValue = canWriteCompressedValue;
            this.compressionTypesValue = compressionTypesValue;
            this.compressionTypeValue = initialCompressionType;
        }

        String compressionType() {
            return compressionTypeValue;
        }

        @Override
        public boolean canWriteCompressed() {
            return canWriteCompressedValue;
        }

        @Override
        public void setCompressionMode(int mode) {
            compressionModeSet = true;
            compressionModeValue = mode;
        }

        @Override
        public int getCompressionMode() {
            return compressionModeValue;
        }

        @Override
        public String[] getCompressionTypes() {
            return compressionTypesValue;
        }

        @Override
        public String getCompressionType() {
            return compressionTypeValue;
        }

        @Override
        public void setCompressionType(String compressionType) {
            compressionTypeSet = true;
            compressionTypeValue = compressionType;
        }

        @Override
        public void setCompressionQuality(float quality) {
            compressionQualitySet = true;
            compressionQualityValue = quality;
        }
    }

    /** Hand-written {@link ImageWriter}: {@code write} is a no-op, everything else just records calls. */
    private static final class FakeImageWriter extends ImageWriter {
        private final FakeWriteParam param;
        boolean writeCalled;
        boolean disposed;

        FakeImageWriter(FakeWriteParam param) {
            super(null);
            this.param = param;
        }

        @Override
        public ImageWriteParam getDefaultWriteParam() {
            return param;
        }

        @Override
        public IIOMetadata getDefaultStreamMetadata(ImageWriteParam writeParam) {
            return null;
        }

        @Override
        public IIOMetadata getDefaultImageMetadata(ImageTypeSpecifier imageType, ImageWriteParam writeParam) {
            return null;
        }

        @Override
        public IIOMetadata convertStreamMetadata(IIOMetadata inData, ImageWriteParam writeParam) {
            return null;
        }

        @Override
        public IIOMetadata convertImageMetadata(IIOMetadata inData, ImageTypeSpecifier imageType,
                ImageWriteParam writeParam) {
            return null;
        }

        @Override
        public void write(IIOMetadata streamMetadata, IIOImage image, ImageWriteParam writeParam) {
            writeCalled = true;
        }

        @Override
        public void dispose() {
            disposed = true;
            super.dispose();
        }
    }

    /** Hand-written {@link ImageWriterSpi} that always hands back the same pre-built fake writer. */
    private static final class FakeWriterSpi extends ImageWriterSpi {
        private final FakeImageWriter writer;

        FakeWriterSpi(String formatName, FakeImageWriter writer) {
            this.vendorName = "pip-core-test";
            this.version = "1.0";
            this.names = new String[] {formatName};
            this.suffixes = new String[] {formatName};
            this.MIMETypes = new String[] {"application/x-pip-core-test"};
            this.pluginClassName = FakeImageWriter.class.getName();
            this.outputTypes = new Class<?>[] {ImageOutputStream.class};
            this.writer = writer;
        }

        @Override
        public String getDescription(Locale locale) {
            return "pip-core test fake writer";
        }

        @Override
        public boolean canEncodeImage(ImageTypeSpecifier type) {
            return true;
        }

        @Override
        public ImageWriter createWriterInstance(Object extension) {
            return writer;
        }
    }
}
