package com.parallelimage.core.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** {@link ValidationException} tests. */
class ValidationExceptionTest {

    @Test
    @DisplayName("the message joins every violation with a semicolon, prefixed by 'invalid request:'")
    void messageJoinsAllViolations() {
        ValidationException exception = new ValidationException(List.of("bad input", "bad output"));

        assertEquals("invalid request: bad input; bad output", exception.getMessage());
    }

    @Test
    @DisplayName("violations() returns every violation passed in, unmodified")
    void violationsReturnsAllOfThem() {
        ValidationException exception = new ValidationException(List.of("a", "b", "c"));

        assertEquals(List.of("a", "b", "c"), exception.violations());
    }

    @Test
    @DisplayName("the single-string constructor delegates to a singleton list")
    void singleStringConstructorDelegates() {
        ValidationException exception = new ValidationException("only one thing wrong");

        assertEquals(List.of("only one thing wrong"), exception.violations());
        assertEquals("invalid request: only one thing wrong", exception.getMessage());
    }

    @Test
    @DisplayName("violations() is immutable")
    void violationsIsImmutable() {
        ValidationException exception = new ValidationException(List.of("a"));

        assertThrows(UnsupportedOperationException.class, () -> exception.violations().add("b"));
    }

    @Test
    @DisplayName("is a PipException")
    void isAPipException() {
        assertTrue(new ValidationException("x") instanceof PipException);
    }
}
