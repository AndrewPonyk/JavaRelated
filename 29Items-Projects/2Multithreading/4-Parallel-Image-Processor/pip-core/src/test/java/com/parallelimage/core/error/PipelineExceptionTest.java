package com.parallelimage.core.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** {@link PipelineException} tests. */
class PipelineExceptionTest {

    @Test
    @DisplayName("the message names the operation and wraps the underlying reason")
    void messageNamesTheOperation() {
        PipelineException exception = new PipelineException("boxBlur", "radius out of range");

        assertEquals("operation 'boxBlur' failed: radius out of range", exception.getMessage());
        assertEquals("boxBlur", exception.operationName());
    }

    @Test
    @DisplayName("the two-argument constructor has no cause")
    void twoArgConstructorHasNoCause() {
        PipelineException exception = new PipelineException("sharpen", "bad kernel");
        assertNull(exception.getCause());
    }

    @Test
    @DisplayName("the three-argument constructor preserves the cause")
    void threeArgConstructorPreservesCause() {
        Throwable cause = new ArithmeticException("divide by zero");
        PipelineException exception = new PipelineException("resize", "scale factor invalid", cause);

        assertSame(cause, exception.getCause());
    }

    @Test
    @DisplayName("is a PipException")
    void isAPipException() {
        assertTrue(new PipelineException("op", "x") instanceof PipException);
    }
}
