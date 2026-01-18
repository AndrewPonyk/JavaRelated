package com.bank.account.exception;

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
 * Global exception handler for the Account Service.
 * Provides consistent error responses across all endpoints.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AccountNotFoundException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleAccountNotFound(
            AccountNotFoundException ex) {
        log.warn("Account not found: {}", ex.getMessage());
        return Mono.just(buildErrorResponse(
            HttpStatus.NOT_FOUND,
            "ACCOUNT_NOT_FOUND",
            ex.getMessage()
        ));
    }

    @ExceptionHandler(DuplicateAccountException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleDuplicateAccount(
            DuplicateAccountException ex) {
        log.warn("Duplicate account: {}", ex.getMessage());
        return Mono.just(buildErrorResponse(
            HttpStatus.CONFLICT,
            "DUPLICATE_ACCOUNT",
            ex.getMessage()
        ));
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleInsufficientFunds(
            InsufficientFundsException ex) {
        log.warn("Insufficient funds: {}", ex.getMessage());
        return Mono.just(buildErrorResponse(
            HttpStatus.BAD_REQUEST,
            "INSUFFICIENT_FUNDS",
            ex.getMessage()
        ));
    }

    @ExceptionHandler(InvalidAccountStateException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleInvalidAccountState(
            InvalidAccountStateException ex) {
        log.warn("Invalid account state: {}", ex.getMessage());
        return Mono.just(buildErrorResponse(
            HttpStatus.BAD_REQUEST,
            "INVALID_ACCOUNT_STATE",
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
