package com.shopflow.common.error;

import org.springframework.http.HttpStatus;

/**
 * Base application exception carrying a stable error {@code code} and the HTTP
 * status it maps to. Services throw subclasses (or this directly) and the shared
 * {@link GlobalExceptionHandler} renders them into the {@code ApiResponse} envelope.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    /** 404 helper. */
    public static ApiException notFound(String resource, Object id) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND",
                "%s with id '%s' was not found".formatted(resource, id));
    }

    /** 409 helper (e.g. idempotency / state conflict). */
    public static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, "CONFLICT", message);
    }

    /** 422 helper (semantically invalid request). */
    public static ApiException unprocessable(String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "UNPROCESSABLE", message);
    }
}
