package com.ehrplatform.fhir.exception;

import com.ehrplatform.common.dto.ApiError;
import com.ehrplatform.common.exception.ResourceNotFoundException;
import com.ehrplatform.common.exception.ValidationException;
import com.ehrplatform.fhir.config.TraceIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates exceptions from the non-FHIR REST controllers into {@link ApiError}.
 *
 * <p>Responses carry a stable code + safe message + traceId — never PHI or stack
 * traces. (FHIR endpoints emit {@code OperationOutcome} via a separate HAPI
 * interceptor; this advice covers the {@code /api/**} surface.)
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getErrorCode(), ex.getMessage());
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiError> handleValidation(ValidationException ex) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, ex.getErrorCode(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleBeanValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .findFirst().orElse("invalid request");
        return build(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED", detail);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        // Log full detail server-side (PHI-safe), return an opaque message to caller.
        log.error("Unhandled exception traceId={}", traceId(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred");
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(ApiError.of(code, message, traceId()));
    }

    private String traceId() {
        String traceId = MDC.get(TraceIdFilter.TRACE_ID);
        return traceId != null ? traceId : "n/a";
    }
}
