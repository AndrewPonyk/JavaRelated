package com.rtap.api.common;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/**
 * One place turns exceptions into RFC 7807 problem-details responses.
 * Philosophy (ARCHITECTURE.md §2.6): clients get actionable, field-level validation
 * errors; 5xx bodies never leak internals — details go to the log with a trace id.
 *
 * <p>Highest precedence so this advice wins over the framework's problem-details
 * handler for the exceptions it enriches (field-level validation errors).
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Body validation failures (@Valid on @RequestBody DTOs). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail onBodyValidation(MethodArgumentNotValidException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setTitle("Validation failed");
        pd.setProperty("errors", ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        fe -> fe.getField(),
                        fe -> String.valueOf(fe.getDefaultMessage()),
                        (a, b) -> a + "; " + b)));
        return pd;
    }

    /** Query/path parameter constraint failures (@Validated on controllers). */
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail onParamValidation(ConstraintViolationException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setTitle("Invalid request parameter");
        pd.setDetail(ex.getMessage());
        return pd;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail onIllegalArgument(IllegalArgumentException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setTitle("Invalid request");
        pd.setDetail(ex.getMessage());
        return pd;
    }

    /** Unparseable query/path values (e.g. a malformed ISO timestamp in ?from=). */
    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public ProblemDetail onTypeMismatch(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setTitle("Invalid parameter");
        pd.setDetail("Parameter '%s' has an invalid value".formatted(ex.getName()));
        return pd;
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ProblemDetail onNotFound(NoSuchElementException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        pd.setTitle("Not found");
        pd.setDetail(ex.getMessage());
        return pd;
    }

    /** Status-bearing exceptions (e.g. 409 conflicts) keep their status — the catch-all
     *  below must not flatten them to 500 now that this advice has highest precedence. */
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ProblemDetail onResponseStatus(org.springframework.web.server.ResponseStatusException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(ex.getStatusCode());
        pd.setTitle(HttpStatus.resolve(ex.getStatusCode().value()) != null
                ? HttpStatus.valueOf(ex.getStatusCode().value()).getReasonPhrase()
                : "Error");
        pd.setDetail(ex.getReason());
        return pd;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail onUnexpected(Exception ex) {
        LOG.error("Unhandled exception", ex); // full detail to logs, not to the client
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        pd.setTitle("Internal error");
        return pd;
    }
}
