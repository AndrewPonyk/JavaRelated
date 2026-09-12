package com.example.pipeline.presentation.cli;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Startup configuration of {@code java.util.logging}.
 *
 * <p><strong>This class mutates JVM-global state</strong> — the root logger's level, its
 * handlers' levels, {@code System.err} and one system property — and surefire reuses a
 * single JVM across test classes. Every test therefore restores what it touched in
 * {@link #restore()}, and the restoration re-applies the packaged configuration rather than
 * trying to reconstruct it, so a later test class sees the same logging setup it would have
 * seen had this class never run.
 *
 * <p>The assertion that earns its keep is {@link #levelIsAppliedToHandlersToo()}: setting the
 * level on the logger alone is the classic {@code java.util.logging} mistake, and it makes
 * {@code --pipeline.log.level=FINE} appear to do nothing at all.
 */
@Timeout(10)
@DisplayName("LoggingSupport")
class LoggingSupportTest {

    private static final Logger ROOT = Logger.getLogger("");

    private PrintStream originalErr;
    private ByteArrayOutputStream capturedErr;

    @BeforeEach
    void captureStderr() {
        originalErr = System.err;
        capturedErr = new ByteArrayOutputStream();
        System.setErr(new PrintStream(capturedErr, true, StandardCharsets.UTF_8));
    }

    @AfterEach
    void restore() {
        System.setErr(originalErr);
        System.clearProperty(LoggingSupport.JDK_CONFIG_PROPERTY);
        // Put the packaged configuration back for whatever test class runs next.
        LoggingSupport.configure("INFO");
    }

    private String stderr() {
        System.err.flush();
        return capturedErr.toString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("the packaged configuration is on the classpath under the documented name")
    void packagedConfigurationIsPresent() throws Exception {
        try (InputStream in = LoggingSupport.class.getClassLoader()
                .getResourceAsStream(LoggingSupport.CONFIG_RESOURCE)) {
            assertNotNull(in, LoggingSupport.CONFIG_RESOURCE + " must be packaged, or logging silently"
                    + " falls back to the JDK default format");
        }
    }

    @Test
    @DisplayName("the system property name is the one the JDK itself honours")
    void jdkPropertyNameIsCorrect() {
        // A typo here would make the documented -D override silently do nothing.
        assertEquals("java.util.logging.config.file", LoggingSupport.JDK_CONFIG_PROPERTY);
    }

    @Test
    @DisplayName("the packaged configuration installs the pipeline formatter on a console handler")
    void packagedConfigurationInstallsTheFormatter() {
        LoggingSupport.configure("INFO");
        Handler[] handlers = ROOT.getHandlers();
        assertEquals(1, handlers.length, "console only: a FileHandler nobody asked for is a surprise");
        assertInstanceOf(java.util.logging.ConsoleHandler.class, handlers[0]);
        assertInstanceOf(PipelineLogFormatter.class, handlers[0].getFormatter(),
                "without this the thread column - the point of the whole setup - is gone");
    }

    /**
     * The trap the production code documents: a handler left at {@code INFO} discards the
     * {@code FINE} records the logger published, so the flag looks broken. Asserting the
     * logger alone would pass against the broken implementation.
     */
    @Test
    @DisplayName("the level is applied to the handlers too, not only to the logger")
    void levelIsAppliedToHandlersToo() {
        LoggingSupport.configure("FINE");
        assertEquals(Level.FINE, ROOT.getLevel());
        for (Handler handler : ROOT.getHandlers()) {
            assertEquals(Level.FINE, handler.getLevel(),
                    "a handler above the logger's level silently drops records");
        }
    }

    @ParameterizedTest
    @DisplayName("a level name is accepted in any case and with surrounding whitespace")
    @ValueSource(strings = {"FINE", "fine", "  Fine  "})
    void levelNamesAreNormalised(String levelName) {
        LoggingSupport.configure(levelName);
        assertEquals(Level.FINE, ROOT.getLevel());
    }

    @Test
    @DisplayName("every level the CLI documents is usable")
    void allDocumentedLevelsParse() {
        for (String name : new String[] {"OFF", "SEVERE", "WARNING", "INFO", "CONFIG", "FINE",
            "FINER", "FINEST", "ALL"}) {
            LoggingSupport.configure(name);
            assertEquals(Level.parse(name), ROOT.getLevel(), name + " should have been applied");
        }
    }

    @Test
    @DisplayName("an unknown level warns and keeps the packaged level instead of failing the run")
    void unknownLevelWarnsAndKeepsInfo() {
        LoggingSupport.configure("VERBOSE"); // A log4j level name; a plausible mistake.
        assertEquals(Level.INFO, ROOT.getLevel(), "the packaged .level=INFO must survive");
        assertTrue(stderr().contains("unknown log level 'VERBOSE'"), stderr());
    }

    @Test
    @DisplayName("a null level degrades to a warning - configure() promises never to throw")
    void nullLevelDoesNotThrow() {
        assertDoesNotThrow(() -> LoggingSupport.configure(null));
        assertTrue(stderr().contains("no log level supplied"), stderr());
        assertEquals(Level.INFO, ROOT.getLevel());
    }

    /**
     * An operator debugging a run must be able to point the JVM at their own file without
     * rebuilding, and this class must then keep its hands off entirely — including not
     * applying the requested level, which would partially override the file they chose.
     */
    @Test
    @DisplayName("an explicit -Djava.util.logging.config.file wins and is left untouched")
    void explicitConfigFilePropertyIsHonoured() {
        LoggingSupport.configure("INFO");
        Level before = ROOT.getLevel();
        System.setProperty(LoggingSupport.JDK_CONFIG_PROPERTY, "does-not-need-to-exist.properties");

        LoggingSupport.configure("FINEST");

        assertEquals(before, ROOT.getLevel(),
                "the operator's file must not be overridden by the level flag");
        assertEquals("", stderr(), "and nothing should be reported about it");
    }
}
