package com.bank.loan.service;

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
 * Handles loan disbursements and payment processing.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountServiceClient {

    private final WebClient.Builder webClientBuilder;

    private static final String ACCOUNT_SERVICE_URL = "http://account-service";

    /**
     * Deposit funds (for loan disbursement).
     */
    @CircuitBreaker(name = "accountService", fallbackMethod = "depositFallback")
    @Retry(name = "accountService")
    public Mono<OperationResult> deposit(UUID accountId, BigDecimal amount,
                                          String currency, String reference) {
        log.info("Depositing {} {} to account {} for loan", amount, currency, accountId);

        return webClientBuilder.build()
            .post()
            .uri(ACCOUNT_SERVICE_URL + "/api/v1/accounts/{accountId}/deposit", accountId)
            .bodyValue(new DepositRequest(amount, currency, reference))
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError, response ->
                response.bodyToMono(ErrorResponse.class)
                    .flatMap(error -> Mono.error(new RuntimeException(error.getMessage()))))
            .bodyToMono(AccountResponse.class)
            .map(resp -> new OperationResult(true, resp.getBalance(), null))
            .doOnSuccess(result -> log.info("Deposit successful for account {}", accountId));
    }

    /**
     * Withdraw funds (for loan payments).
     */
    @CircuitBreaker(name = "accountService", fallbackMethod = "withdrawFallback")
    @Retry(name = "accountService")
    public Mono<OperationResult> withdraw(UUID accountId, BigDecimal amount,
                                           String currency, String reference) {
        log.info("Withdrawing {} {} from account {} for loan payment", amount, currency, accountId);

        return webClientBuilder.build()
            .post()
            .uri(ACCOUNT_SERVICE_URL + "/api/v1/accounts/{accountId}/withdraw", accountId)
            .bodyValue(new WithdrawRequest(amount, currency, reference))
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError, response ->
                response.bodyToMono(ErrorResponse.class)
                    .flatMap(error -> Mono.error(new RuntimeException(error.getMessage()))))
            .bodyToMono(AccountResponse.class)
            .map(resp -> new OperationResult(true, resp.getBalance(), null))
            .doOnSuccess(result -> log.info("Withdrawal successful for account {}", accountId));
    }

    public Mono<OperationResult> depositFallback(UUID accountId, BigDecimal amount,
                                                  String currency, String reference, Throwable ex) {
        log.error("Deposit failed for account {}: {}", accountId, ex.getMessage());
        return Mono.just(new OperationResult(false, null, ex.getMessage()));
    }

    public Mono<OperationResult> withdrawFallback(UUID accountId, BigDecimal amount,
                                                   String currency, String reference, Throwable ex) {
        log.error("Withdrawal failed for account {}: {}", accountId, ex.getMessage());
        return Mono.just(new OperationResult(false, null, ex.getMessage()));
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
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class WithdrawRequest {
        private BigDecimal amount;
        private String currency;
        private String reference;
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class AccountResponse {
        private UUID id;
        private BigDecimal balance;
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
    public static class OperationResult {
        private boolean success;
        private BigDecimal newBalance;
        private String errorMessage;
    }
}
