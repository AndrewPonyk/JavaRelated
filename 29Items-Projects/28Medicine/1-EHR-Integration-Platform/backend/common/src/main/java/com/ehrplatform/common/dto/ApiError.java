package com.ehrplatform.common.dto;

import java.time.Instant;

/**
 * PHI-safe error payload returned by non-FHIR REST endpoints.
 *
 * <p>Never include clinical content or patient identifiers here — only a stable
 * {@code code}, a safe human-readable {@code message}, and a {@code traceId} that
 * correlates to server-side logs for support investigations.
 *
 * @param code      stable machine-readable error code (e.g. {@code VALIDATION_FAILED})
 * @param message   safe, non-leaking description
 * @param traceId   correlation id propagated from the request (W3C traceparent)
 * @param timestamp server time the error was produced
 */
public record ApiError(String code, String message, String traceId, Instant timestamp) {

    public static ApiError of(String code, String message, String traceId) {
        return new ApiError(code, message, traceId, Instant.now());
    }
}
