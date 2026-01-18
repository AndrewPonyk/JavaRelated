package com.bank.loan.exception;

import com.bank.loan.service.LoanApplicationService;
import com.bank.loan.service.LoanDisbursementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global exception handler for the Loan Service.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(LoanApplicationService.ApplicationNotFoundException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleApplicationNotFound(
            LoanApplicationService.ApplicationNotFoundException ex) {
        log.warn("Application not found: {}", ex.getMessage());
        return Mono.just(buildErrorResponse(
            HttpStatus.NOT_FOUND,
            "APPLICATION_NOT_FOUND",
            ex.getMessage()
        ));
    }

    @ExceptionHandler(LoanDisbursementService.LoanNotFoundException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleLoanNotFound(
            LoanDisbursementService.LoanNotFoundException ex) {
        log.warn("Loan not found: {}", ex.getMessage());
        return Mono.just(buildErrorResponse(
            HttpStatus.NOT_FOUND,
            "LOAN_NOT_FOUND",
            ex.getMessage()
        ));
    }

    @ExceptionHandler(LoanDisbursementService.DisbursementException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleDisbursementError(
            LoanDisbursementService.DisbursementException ex) {
        log.error("Disbursement error: {}", ex.getMessage());
        return Mono.just(buildErrorResponse(
            HttpStatus.BAD_REQUEST,
            "DISBURSEMENT_ERROR",
            ex.getMessage()
        ));
    }

    @ExceptionHandler(LoanDisbursementService.PaymentException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handlePaymentError(
            LoanDisbursementService.PaymentException ex) {
        log.error("Payment error: {}", ex.getMessage());
        return Mono.just(buildErrorResponse(
            HttpStatus.BAD_REQUEST,
            "PAYMENT_ERROR",
            ex.getMessage()
        ));
    }

    @ExceptionHandler(IllegalStateException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleIllegalState(
            IllegalStateException ex) {
        log.warn("Illegal state: {}", ex.getMessage());
        return Mono.just(buildErrorResponse(
            HttpStatus.BAD_REQUEST,
            "INVALID_OPERATION",
            ex.getMessage()
        ));
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleValidation(
            WebExchangeBindException ex) {
        String errors = ex.getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .collect(Collectors.joining(", "));

        log.warn("Validation error: {}", errors);
        return Mono.just(buildErrorResponse(
            HttpStatus.BAD_REQUEST,
            "VALIDATION_ERROR",
            errors
        ));
    }

    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleGeneral(Exception ex) {
        log.error("Unexpected error", ex);
        return Mono.just(buildErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "INTERNAL_ERROR",
            "An unexpected error occurred"
        ));
    }

    private ResponseEntity<Map<String, Object>> buildErrorResponse(
            HttpStatus status,
            String code,
            String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("code", code);
        error.put("message", message);
        error.put("timestamp", Instant.now().toString());

        Map<String, Object> body = new HashMap<>();
        body.put("error", error);

        return ResponseEntity.status(status).body(body);
    }
}
