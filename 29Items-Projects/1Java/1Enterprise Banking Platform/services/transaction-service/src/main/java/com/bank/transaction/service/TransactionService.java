package com.bank.transaction.service;

import com.bank.transaction.dto.TransactionRequest;
import com.bank.transaction.dto.TransactionResponse;
import com.bank.transaction.model.Transaction;
import com.bank.transaction.model.TransactionStatus;
import com.bank.transaction.repository.TransactionRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Service layer for transaction processing.
 * Implements CQRS pattern with fraud detection integration and Saga for transfers.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final FraudCheckClient fraudCheckClient;
    private final AccountServiceClient accountServiceClient;
    private final ReferenceNumberGenerator referenceGenerator;

    private static final double RISK_THRESHOLD = 0.7;

    /**
     * Initiate a fund transfer with fraud check.
     */
    @Transactional
    @CircuitBreaker(name = "fraudService", fallbackMethod = "initiateTransferFallback")
    @Retry(name = "fraudService")
    public Mono<TransactionResponse> initiateTransfer(TransactionRequest request) {
        log.info("Initiating transfer: {} -> {}, amount: {}",
            request.getSourceAccountId(),
            request.getTargetAccountId(),
            request.getAmount());

        return referenceGenerator.generate()
            .flatMap(reference -> {
                Transaction transaction = Transaction.builder()
                    .id(UUID.randomUUID())
                    .referenceNumber(reference)
                    .sourceAccountId(request.getSourceAccountId())
                    .targetAccountId(request.getTargetAccountId())
                    .transactionType(request.getTransactionType())
                    .amount(request.getAmount())
                    .currency(request.getCurrency())
                    .description(request.getDescription())
                    .status(TransactionStatus.INITIATED)
                    .initiatedAt(Instant.now())
                    .build();

                return transactionRepository.save(transaction);
            })
            .flatMap(transaction ->
                fraudCheckClient.checkFraud(transaction)
                    .flatMap(riskScore -> {
                        transaction.setRiskScore(riskScore);
                        if (riskScore > RISK_THRESHOLD) {
                            log.warn("Transaction flagged for review: {}, risk: {}",
                                transaction.getId(), riskScore);
                            transaction.flagForReview();
                            return Mono.just(transaction);
                        } else {
                            // Execute fund transfer using Saga pattern
                            return executeFundTransferSaga(transaction);
                        }
                    })
            )
            .flatMap(transactionRepository::save)
            .map(this::mapToResponse)
            .doOnSuccess(response ->
                log.info("Transfer completed: {}, status: {}",
                    response.getReferenceNumber(), response.getStatus()));
    }

    /**
     * Execute the fund transfer using Saga pattern.
     * Steps: 1) Debit source account, 2) Credit target account
     * Compensation: If credit fails, compensate by crediting back to source
     */
    private Mono<Transaction> executeFundTransferSaga(Transaction transaction) {
        log.info("Executing fund transfer saga for transaction: {}", transaction.getId());

        // Step 1: Debit source account
        return accountServiceClient.debit(
                transaction.getSourceAccountId(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getReferenceNumber()
            )
            .flatMap(debitResult -> {
                if (!debitResult.isSuccess()) {
                    log.error("Debit failed: {}", debitResult.getErrorMessage());
                    transaction.fail("Debit failed: " + debitResult.getErrorMessage());
                    return Mono.just(transaction);
                }

                // Step 2: Credit target account
                return accountServiceClient.credit(
                        transaction.getTargetAccountId(),
                        transaction.getAmount(),
                        transaction.getCurrency(),
                        transaction.getReferenceNumber()
                    )
                    .flatMap(creditResult -> {
                        if (!creditResult.isSuccess()) {
                            // Compensation: Credit back to source account
                            log.error("Credit failed, initiating compensation: {}",
                                creditResult.getErrorMessage());
                            return accountServiceClient.compensateDebit(
                                    transaction.getSourceAccountId(),
                                    transaction.getAmount(),
                                    transaction.getCurrency(),
                                    transaction.getReferenceNumber()
                                )
                                .map(compensationResult -> {
                                    transaction.fail("Credit failed: " + creditResult.getErrorMessage() +
                                        ". Funds returned to source account.");
                                    return transaction;
                                });
                        }

                        // Both steps successful
                        transaction.complete();
                        log.info("Fund transfer saga completed successfully: {}", transaction.getId());
                        return Mono.just(transaction);
                    });
            })
            .onErrorResume(error -> {
                log.error("Fund transfer saga failed unexpectedly: {}", error.getMessage(), error);
                transaction.fail("Transfer failed: " + error.getMessage());
                return Mono.just(transaction);
            });
    }

    /**
     * Fallback when fraud service is unavailable.
     */
    public Mono<TransactionResponse> initiateTransferFallback(
            TransactionRequest request, Throwable ex) {
        log.warn("Fraud service unavailable, flagging for manual review", ex);

        return referenceGenerator.generate()
            .flatMap(reference -> {
                Transaction transaction = Transaction.builder()
                    .id(UUID.randomUUID())
                    .referenceNumber(reference)
                    .sourceAccountId(request.getSourceAccountId())
                    .targetAccountId(request.getTargetAccountId())
                    .transactionType(request.getTransactionType())
                    .amount(request.getAmount())
                    .currency(request.getCurrency())
                    .description(request.getDescription())
                    .status(TransactionStatus.PENDING_REVIEW)
                    .initiatedAt(Instant.now())
                    .build();

                return transactionRepository.save(transaction);
            })
            .map(this::mapToResponse);
    }

    /**
     * Get transaction by ID.
     */
    public Mono<TransactionResponse> getTransaction(UUID transactionId) {
        return transactionRepository.findById(transactionId)
            .map(this::mapToResponse)
            .switchIfEmpty(Mono.error(new TransactionNotFoundException(transactionId)));
    }

    /**
     * Get transactions for an account.
     */
    public Flux<TransactionResponse> getTransactionsByAccount(
            UUID accountId, int page, int size) {
        return transactionRepository.findBySourceAccountIdOrTargetAccountId(
                accountId, accountId)
            .skip((long) page * size)
            .take(size)
            .map(this::mapToResponse);
    }

    /**
     * Cancel a pending transaction.
     */
    @Transactional
    public Mono<TransactionResponse> cancelTransaction(UUID transactionId) {
        return transactionRepository.findById(transactionId)
            .switchIfEmpty(Mono.error(new TransactionNotFoundException(transactionId)))
            .flatMap(transaction -> {
                if (transaction.getStatus() != TransactionStatus.INITIATED &&
                    transaction.getStatus() != TransactionStatus.PENDING_REVIEW) {
                    return Mono.error(new IllegalStateException(
                        "Cannot cancel transaction in status: " + transaction.getStatus()));
                }
                transaction.setStatus(TransactionStatus.CANCELLED);
                return transactionRepository.save(transaction);
            })
            .map(this::mapToResponse);
    }

    /**
     * Approve a pending review transaction and execute the transfer.
     */
    @Transactional
    public Mono<TransactionResponse> approveTransaction(UUID transactionId) {
        log.info("Approving transaction: {}", transactionId);

        return transactionRepository.findById(transactionId)
            .switchIfEmpty(Mono.error(new TransactionNotFoundException(transactionId)))
            .flatMap(transaction -> {
                if (transaction.getStatus() != TransactionStatus.PENDING_REVIEW) {
                    return Mono.error(new IllegalStateException(
                        "Can only approve transactions in PENDING_REVIEW status. Current: " +
                            transaction.getStatus()));
                }
                return executeFundTransferSaga(transaction);
            })
            .flatMap(transactionRepository::save)
            .map(this::mapToResponse);
    }

    /**
     * Reject a pending review transaction.
     */
    @Transactional
    public Mono<TransactionResponse> rejectTransaction(UUID transactionId, String reason) {
        log.info("Rejecting transaction: {}, reason: {}", transactionId, reason);

        return transactionRepository.findById(transactionId)
            .switchIfEmpty(Mono.error(new TransactionNotFoundException(transactionId)))
            .flatMap(transaction -> {
                if (transaction.getStatus() != TransactionStatus.PENDING_REVIEW) {
                    return Mono.error(new IllegalStateException(
                        "Can only reject transactions in PENDING_REVIEW status. Current: " +
                            transaction.getStatus()));
                }
                transaction.fail("Rejected: " + reason);
                return transactionRepository.save(transaction);
            })
            .map(this::mapToResponse);
    }

    /**
     * Get transactions by reference number.
     */
    public Mono<TransactionResponse> getTransactionByReference(String referenceNumber) {
        return transactionRepository.findByReferenceNumber(referenceNumber)
            .switchIfEmpty(Mono.error(new TransactionNotFoundException(referenceNumber)))
            .map(this::mapToResponse);
    }

    /**
     * Get transactions in a date range for an account.
     */
    public Flux<TransactionResponse> getTransactionsByDateRange(
            UUID accountId, Instant startDate, Instant endDate) {
        return transactionRepository.findByAccountAndDateRange(accountId, startDate, endDate)
            .map(this::mapToResponse);
    }

    /**
     * Get all pending review transactions.
     */
    public Flux<TransactionResponse> getPendingReviewTransactions() {
        return transactionRepository.findPendingReviewTransactions()
            .map(this::mapToResponse);
    }

    /**
     * Get transaction statistics.
     */
    public Mono<TransactionStats> getTransactionStats() {
        return Mono.zip(
            transactionRepository.countByStatus(TransactionStatus.COMPLETED),
            transactionRepository.countByStatus(TransactionStatus.FAILED),
            transactionRepository.countByStatus(TransactionStatus.PENDING_REVIEW),
            transactionRepository.countByStatus(TransactionStatus.CANCELLED)
        ).map(tuple -> new TransactionStats(
            tuple.getT1(), tuple.getT2(), tuple.getT3(), tuple.getT4()
        ));
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class TransactionStats {
        private long completed;
        private long failed;
        private long pendingReview;
        private long cancelled;
    }

    // Exception classes
    public static class TransactionNotFoundException extends RuntimeException {
        public TransactionNotFoundException(UUID transactionId) {
            super("Transaction not found: " + transactionId);
        }
        public TransactionNotFoundException(String reference) {
            super("Transaction not found with reference: " + reference);
        }
    }

    private TransactionResponse mapToResponse(Transaction transaction) {
        return TransactionResponse.builder()
            .id(transaction.getId())
            .referenceNumber(transaction.getReferenceNumber())
            .sourceAccountId(transaction.getSourceAccountId())
            .targetAccountId(transaction.getTargetAccountId())
            .transactionType(transaction.getTransactionType())
            .amount(transaction.getAmount())
            .currency(transaction.getCurrency())
            .description(transaction.getDescription())
            .status(transaction.getStatus())
            .initiatedAt(transaction.getInitiatedAt())
            .completedAt(transaction.getCompletedAt())
            .failureReason(transaction.getFailureReason())
            .build();
    }
}
