package com.parallelimage.core.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** {@link ImageIoException} tests. */
class ImageIoExceptionTest {

    @Test
    @DisplayName("the offending path is appended to the message in brackets")
    void pathIsAppendedToMessage() {
        Path path = Path.of("input", "broken.png");
        ImageIoException exception = new ImageIoException(path, "could not decode");

        assertEquals("could not decode [" + path + "]", exception.getMessage());
        assertSame(path, exception.path());
    }

    @Test
    @DisplayName("the two-argument constructor has no cause")
    void twoArgConstructorHasNoCause() {
        ImageIoException exception = new ImageIoException(Path.of("a.png"), "nope");
        assertNull(exception.getCause());
    }

    @Test
    @DisplayName("the three-argument constructor preserves the cause")
    void threeArgConstructorPreservesCause() {
        Throwable cause = new java.io.IOException("disk error");
        ImageIoException exception = new ImageIoException(Path.of("a.png"), "write failed", cause);

        assertSame(cause, exception.getCause());
    }

    @Test
    @DisplayName("is a PipException")
    void isAPipException() {
        assertTrue(new ImageIoException(Path.of("a.png"), "x") instanceof PipException);
    }
}
