package com.shopflow.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/**
 * Uniform response envelope returned by every ShopFlow service.
 *
 * <p>Success: {@code {"success": true, "data": ... }}.
 * Failure: {@code {"success": false, "error": {code, message, fieldErrors}}}.
 * Keeping one shape across services makes the frontend and gateway error
 * handling trivial and consistent.
 *
 * @param <T> payload type on success
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        T data,
        ApiError error,
        Instant timestamp,
        String traceId) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, Instant.now(), null);
    }

    public static <T> ApiResponse<T> ok(T data, String traceId) {
        return new ApiResponse<>(true, data, null, Instant.now(), traceId);
    }

    public static <T> ApiResponse<T> fail(ApiError error, String traceId) {
        return new ApiResponse<>(false, null, error, Instant.now(), traceId);
    }

    /** Machine-readable error body (aligns with RFC-7807 problem details). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ApiError(String code, String message, List<FieldError> fieldErrors) {
        public static ApiError of(String code, String message) {
            return new ApiError(code, message, null);
        }
    }

    /** Per-field validation error. */
    public record FieldError(String field, String message) { }
}
