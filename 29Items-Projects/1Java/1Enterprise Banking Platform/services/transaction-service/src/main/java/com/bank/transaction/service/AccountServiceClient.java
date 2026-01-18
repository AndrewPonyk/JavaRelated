package com.bank.transaction.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Client for Account Service.
 * Handles fund transfers between accounts with saga compensation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountServiceClient {

    private final WebClient.Builder webClientBuilder;

    private static final String ACCOUNT_SERVICE_URL = "http://account-service";

    /**
     * Debit funds from a source account.
     *
     * @param accountId the account to debit
     * @param amount the amount to debit
     * @param currency the currency
     * @param reference the transaction reference
     * @return Mono signaling completion
     */
    @CircuitBreaker(name = "accountService", fallbackMethod = "debitFallback")
    @Retry(name = "accountService")
    public Mono<AccountOperationResult> debit(UUID accountId, BigDecimal amount,
                                               String currency, String reference) {
        log.info("Debiting {} {} from account {}", amount, currency, accountId);

        return webClientBuilder.build()
            .post()
            .uri(ACCOUNT_SERVICE_URL + "/api/v1/accounts/{accountId}/withdraw", accountId)
            .bodyValue(new WithdrawRequest(amount, currency, reference))
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError, response ->
                response.bodyToMono(ErrorResponse.class)
                    .flatMap(error -> Mono.error(new AccountOperationException(
                        error.getMessage(), error.getCode()))))
            .bodyToMono(AccountResponse.class)
            .map(resp -> new AccountOperationResult(true, resp.getBalance(), null))
            .doOnSuccess(result -> log.info("Debit successful for account {}", accountId));
    }

    /**
     * Credit funds to a target account.
     *
     * @param accountId the account to credit
     * @param amount the amount to credit
     * @param currency the currency
     * @param reference the transaction reference
     * @return Mono signaling completion
     */
    @CircuitBreaker(name = "accountService", fallbackMethod = "creditFallback")
    @Retry(name = "accountService")
    public Mono<AccountOperationResult> credit(UUID accountId, BigDecimal amount,
                                                String currency, String reference) {
        log.info("Crediting {} {} to account {}", amount, currency, accountId);

        return webClientBuilder.build()
            .post()
            .uri(ACCOUNT_SERVICE_URL + "/api/v1/accounts/{accountId}/deposit", accountId)
            .bodyValue(new DepositRequest(amount, currency, reference))
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError, response ->
                response.bodyToMono(ErrorResponse.class)
                    .flatMap(error -> Mono.error(new AccountOperationException(
                        error.getMessage(), error.getCode()))))
            .bodyToMono(AccountResponse.class)
            .map(resp -> new AccountOperationResult(true, resp.getBalance(), null))
            .doOnSuccess(result -> log.info("Credit successful for account {}", accountId));
    }

    /**
     * Compensate a debit operation (rollback).
     */
    public Mono<AccountOperationResult> compensateDebit(UUID accountId, BigDecimal amount,
                                                         String currency, String reference) {
        log.warn("Compensating debit: crediting {} {} back to account {}", amount, currency, accountId);
        return credit(accountId, amount, currency, "COMPENSATION-" + reference);
    }

    /**
     * Compensate a credit operation (rollback).
     */
    public Mono<AccountOperationResult> compensateCredit(UUID accountId, BigDecimal amount,
                                                          String currency, String reference) {
        log.warn("Compensating credit: debiting {} {} from account {}", amount, currency, accountId);
        return debit(accountId, amount, currency, "COMPENSATION-" + reference);
    }

    /**
     * Fallback when account service debit fails.
     */
    public Mono<AccountOperationResult> debitFallback(UUID accountId, BigDecimal amount,
                                                       String currency, String reference, Throwable ex) {
        log.error("Debit failed for account {}: {}", accountId, ex.getMessage());
        return Mono.just(new AccountOperationResult(false, null, ex.getMessage()));
    }

    /**
     * Fallback when account service credit fails.
     */
    public Mono<AccountOperationResult> creditFallback(UUID accountId, BigDecimal amount,
                                                        String currency, String reference, Throwable ex) {
        log.error("Credit failed for account {}: {}", accountId, ex.getMessage());
        return Mono.just(new AccountOperationResult(false, null, ex.getMessage()));
    }

    // Inner classes for request/response
    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class WithdrawRequest {
        private BigDecimal amount;
        private String currency;
        private String reference;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class DepositRequest {
        private BigDecimal amount;
        private String currency;
        private String reference;
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class AccountResponse {
        private UUID id;
        private String accountNumber;
        private BigDecimal balance;
        private String status;
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ErrorResponse {
        private String code;
        private String message;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class AccountOperationResult {
        private boolean success;
        private BigDecimal newBalance;
        private String errorMessage;
    }

    public static class AccountOperationException extends RuntimeException {
        private final String code;

        public AccountOperationException(String message, String code) {
            super(message);
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }
}
