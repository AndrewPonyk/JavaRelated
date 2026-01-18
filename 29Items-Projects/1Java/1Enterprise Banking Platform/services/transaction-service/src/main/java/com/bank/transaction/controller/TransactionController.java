package com.bank.transaction.controller;

import com.bank.transaction.dto.TransactionRequest;
import com.bank.transaction.dto.TransactionResponse;
import com.bank.transaction.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * REST controller for transaction operations.
 * Implements CQRS pattern with separate command and query endpoints.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Tag(name = "Transactions", description = "Transaction processing APIs")
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping("/transfer")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Initiate fund transfer", description = "Creates a new fund transfer transaction")
    public Mono<TransactionResponse> initiateTransfer(
            @Valid @RequestBody TransactionRequest request) {
        log.info("Received transfer request: {} -> {}",
            request.getSourceAccountId(), request.getTargetAccountId());
        return transactionService.initiateTransfer(request);
    }

    @GetMapping("/{transactionId}")
    @Operation(summary = "Get transaction by ID", description = "Retrieves transaction details")
    public Mono<TransactionResponse> getTransaction(
            @PathVariable UUID transactionId) {
        log.debug("Fetching transaction: {}", transactionId);
        return transactionService.getTransaction(transactionId);
    }

    @GetMapping(value = "/account/{accountId}", produces = MediaType.APPLICATION_NDJSON_VALUE)
    @Operation(summary = "Get account transactions", description = "Retrieves transaction history for an account")
    public Flux<TransactionResponse> getAccountTransactions(
            @PathVariable UUID accountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.debug("Fetching transactions for account: {}", accountId);
        return transactionService.getTransactionsByAccount(accountId, page, size);
    }

    @PostMapping("/{transactionId}/cancel")
    @Operation(summary = "Cancel transaction", description = "Cancels a pending transaction")
    public Mono<TransactionResponse> cancelTransaction(
            @PathVariable UUID transactionId) {
        log.info("Cancelling transaction: {}", transactionId);
        return transactionService.cancelTransaction(transactionId);
    }

    @PostMapping("/{transactionId}/approve")
    @Operation(summary = "Approve transaction", description = "Approves a pending review transaction")
    public Mono<TransactionResponse> approveTransaction(
            @PathVariable UUID transactionId) {
        log.info("Approving transaction: {}", transactionId);
        return transactionService.approveTransaction(transactionId);
    }

    @PostMapping("/{transactionId}/reject")
    @Operation(summary = "Reject transaction", description = "Rejects a pending review transaction")
    public Mono<TransactionResponse> rejectTransaction(
            @PathVariable UUID transactionId,
            @RequestParam @NotBlank @Size(max = 500) String reason) {
        log.info("Rejecting transaction: {}", transactionId);
        return transactionService.rejectTransaction(transactionId, reason);
    }

    @GetMapping("/reference/{referenceNumber}")
    @Operation(summary = "Get transaction by reference", description = "Retrieves transaction by reference number")
    public Mono<TransactionResponse> getTransactionByReference(
            @PathVariable String referenceNumber) {
        log.debug("Fetching transaction by reference: {}", referenceNumber);
        return transactionService.getTransactionByReference(referenceNumber);
    }

    @GetMapping(value = "/pending-review", produces = MediaType.APPLICATION_NDJSON_VALUE)
    @Operation(summary = "Get pending review transactions", description = "Retrieves all transactions pending review")
    public Flux<TransactionResponse> getPendingReviewTransactions() {
        log.debug("Fetching pending review transactions");
        return transactionService.getPendingReviewTransactions();
    }

    @GetMapping("/stats")
    @Operation(summary = "Get transaction statistics", description = "Retrieves transaction statistics by status")
    public Mono<TransactionService.TransactionStats> getTransactionStats() {
        log.debug("Fetching transaction statistics");
        return transactionService.getTransactionStats();
    }
}
