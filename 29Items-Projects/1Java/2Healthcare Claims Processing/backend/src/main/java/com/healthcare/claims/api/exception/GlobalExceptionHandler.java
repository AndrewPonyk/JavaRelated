package com.healthcare.claims.api.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Global exception handler for REST API errors.
 */
@Provider
public class GlobalExceptionHandler implements ExceptionMapper<Throwable> {

    private static final Logger LOG = Logger.getLogger(GlobalExceptionHandler.class);

    @Override
    public Response toResponse(Throwable exception) {
        String traceId = UUID.randomUUID().toString();
        LOG.errorf(exception, "Error processing request [traceId=%s]", traceId);

        if (exception instanceof ResourceNotFoundException e) {
            return buildResponse(Response.Status.NOT_FOUND, e.getErrorCode(), e.getMessage(), traceId);
        }

        if (exception instanceof DuplicateResourceException e) {
            return buildResponse(Response.Status.CONFLICT, e.getErrorCode(), e.getMessage(), traceId);
        }

        if (exception instanceof ValidationException e) {
            return buildValidationResponse(e, traceId);
        }

        if (exception instanceof InvalidStateTransitionException e) {
            return buildResponse(Response.Status.BAD_REQUEST, e.getErrorCode(), e.getMessage(), traceId);
        }

        if (exception instanceof ClaimProcessingException e) {
            return buildResponse(422, e.getErrorCode(), e.getMessage(), traceId);
        }

        if (exception instanceof FraudDetectionException e) {
            return buildResponse(Response.Status.SERVICE_UNAVAILABLE, e.getErrorCode(), e.getMessage(), traceId);
        }

        if (exception instanceof BusinessException e) {
            return buildResponse(Response.Status.BAD_REQUEST, e.getErrorCode(), e.getMessage(), traceId);
        }

        if (exception instanceof ConstraintViolationException e) {
            return buildConstraintViolationResponse(e, traceId);
        }

        if (exception instanceof IllegalArgumentException e) {
            return buildResponse(Response.Status.BAD_REQUEST, "BAD_REQUEST", e.getMessage(), traceId);
        }

        // Default internal server error
        return buildResponse(
            Response.Status.INTERNAL_SERVER_ERROR,
            "INTERNAL_ERROR",
            "An unexpected error occurred. Please try again later.",
            traceId
        );
    }

    private Response buildResponse(Response.Status status, String errorCode, String message, String traceId) {
        return buildResponse(status.getStatusCode(), errorCode, message, traceId);
    }

    private Response buildResponse(int statusCode, String errorCode, String message, String traceId) {
        ErrorResponse errorResponse = new ErrorResponse(
            errorCode,
            message,
            null,
            traceId,
            Instant.now()
        );
        return Response.status(statusCode).entity(errorResponse).build();
    }

    private Response buildValidationResponse(ValidationException e, String traceId) {
        List<FieldError> fieldErrors = e.getErrors().stream()
            .map(err -> new FieldError(err.field(), err.message()))
            .toList();

        ErrorResponse errorResponse = new ErrorResponse(
            e.getErrorCode(),
            "Validation failed",
            fieldErrors,
            traceId,
            Instant.now()
        );
        return Response.status(Response.Status.BAD_REQUEST).entity(errorResponse).build();
    }

    private Response buildConstraintViolationResponse(ConstraintViolationException e, String traceId) {
        Set<ConstraintViolation<?>> violations = e.getConstraintViolations();

        List<FieldError> fieldErrors = violations.stream()
            .map(v -> new FieldError(
                getFieldName(v.getPropertyPath().toString()),
                v.getMessage()
            ))
            .collect(Collectors.toList());

        ErrorResponse errorResponse = new ErrorResponse(
            "VALIDATION_ERROR",
            "Validation failed",
            fieldErrors,
            traceId,
            Instant.now()
        );
        return Response.status(Response.Status.BAD_REQUEST).entity(errorResponse).build();
    }

    private String getFieldName(String propertyPath) {
        if (propertyPath == null || propertyPath.isEmpty()) {
            return "unknown";
        }
        int lastDot = propertyPath.lastIndexOf('.');
        return lastDot >= 0 ? propertyPath.substring(lastDot + 1) : propertyPath;
    }

    public record ErrorResponse(
        String errorCode,
        String message,
        List<FieldError> fieldErrors,
        String traceId,
        Instant timestamp
    ) {}

    public record FieldError(String field, String message) {}
}
