package com.shopflow.common.error;

import com.shopflow.common.dto.ApiResponse;
import com.shopflow.common.dto.ApiResponse.ApiError;
import com.shopflow.common.dto.ApiResponse.FieldError;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Shared exception-to-response mapper. Imported by every web service via
 * component scan so error responses are identical platform-wide. Returns the
 * standard {@link ApiResponse} envelope and never leaks stack traces to clients.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Domain errors thrown with an explicit status + code. */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApi(ApiException ex) {
        log.warn("API exception [{}]: {}", ex.getCode(), ex.getMessage());
        return ResponseEntity.status(ex.getStatus())
                .body(ApiResponse.fail(ApiError.of(ex.getCode(), ex.getMessage()), traceId()));
    }

    /** Bean-validation failures on {@code @Valid @RequestBody} arguments. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        List<FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        ApiError error = new ApiError("VALIDATION_FAILED", "Request validation failed", fieldErrors);
        return ResponseEntity.badRequest().body(ApiResponse.fail(error, traceId()));
    }

    /** Illegal state (e.g. an invalid domain transition) → 409 Conflict. */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalState(IllegalStateException ex) {
        log.warn("Illegal state: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail(ApiError.of("CONFLICT", ex.getMessage()), traceId()));
    }

    /** Bad argument that slipped past bean validation → 400 Bad Request. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Illegal argument: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.fail(ApiError.of("BAD_REQUEST", ex.getMessage()), traceId()));
    }

    /** Last-resort handler: log full detail server-side, return opaque 500. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        ApiError error = ApiError.of("INTERNAL_ERROR", "An unexpected error occurred");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail(error, traceId()));
    }

    /** Correlation id placed in MDC by the tracing filter, surfaced to clients. */
    private static String traceId() {
        return MDC.get("traceId");
    }
}
