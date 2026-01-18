package com.bank.gateway.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

/**
 * Fallback controller for handling service unavailability.
 */
@Slf4j
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @GetMapping(value = "/account", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Map<String, Object>>> accountServiceFallback() {
        log.warn("Account service is unavailable");
        return createFallbackResponse("ACCOUNT_SERVICE_UNAVAILABLE",
            "Account service is temporarily unavailable. Please try again later.");
    }

    @GetMapping(value = "/transaction", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Map<String, Object>>> transactionServiceFallback() {
        log.warn("Transaction service is unavailable");
        return createFallbackResponse("TRANSACTION_SERVICE_UNAVAILABLE",
            "Transaction service is temporarily unavailable. Please try again later.");
    }

    @GetMapping(value = "/loan", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Map<String, Object>>> loanServiceFallback() {
        log.warn("Loan service is unavailable");
        return createFallbackResponse("LOAN_SERVICE_UNAVAILABLE",
            "Loan service is temporarily unavailable. Please try again later.");
    }

    @GetMapping(value = "/fraud", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Map<String, Object>>> fraudServiceFallback() {
        log.warn("Fraud detection service is unavailable");
        return createFallbackResponse("FRAUD_SERVICE_UNAVAILABLE",
            "Fraud detection service is temporarily unavailable. Transaction may be delayed.");
    }

    private Mono<ResponseEntity<Map<String, Object>>> createFallbackResponse(String code, String message) {
        Map<String, Object> error = Map.of(
            "code", code,
            "message", message,
            "timestamp", Instant.now().toString()
        );

        Map<String, Object> response = Map.of("error", error);

        return Mono.just(ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(response));
    }
}
