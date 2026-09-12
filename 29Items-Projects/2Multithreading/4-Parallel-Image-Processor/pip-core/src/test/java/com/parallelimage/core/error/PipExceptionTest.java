package com.parallelimage.core.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** {@link PipException} tests. */
class PipExceptionTest {

    @Test
    @DisplayName("the message-only constructor carries the message and no cause")
    void messageOnlyConstructor() {
        PipException exception = new PipException("something broke");

        assertEquals("something broke", exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    @DisplayName("the message+cause constructor preserves both")
    void messageAndCauseConstructor() {
        Throwable cause = new IllegalStateException("root cause");
        PipException exception = new PipException("wrapped", cause);

        assertEquals("wrapped", exception.getMessage());
        assertSame(cause, exception.getCause());
    }

    @Test
    @DisplayName("is an unchecked exception")
    void isUnchecked() {
        assertTrue(new PipException("x") instanceof RuntimeException);
    }
}
