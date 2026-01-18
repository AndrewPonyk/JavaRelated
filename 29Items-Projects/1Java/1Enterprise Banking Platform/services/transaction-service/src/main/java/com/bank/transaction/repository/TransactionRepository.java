package com.bank.transaction.repository;

import com.bank.transaction.model.Transaction;
import com.bank.transaction.model.TransactionStatus;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Reactive repository for Transaction entity.
 */
@Repository
public interface TransactionRepository extends R2dbcRepository<Transaction, UUID> {

    /**
     * Find transaction by reference number.
     */
    Mono<Transaction> findByReferenceNumber(String referenceNumber);

    /**
     * Find transactions by source or target account.
     */
    Flux<Transaction> findBySourceAccountIdOrTargetAccountId(
        UUID sourceAccountId, UUID targetAccountId);

    /**
     * Find transactions by status.
     */
    Flux<Transaction> findByStatus(TransactionStatus status);

    /**
     * Find transactions for an account within a date range.
     */
    @Query("SELECT * FROM transactions " +
           "WHERE (source_account_id = :accountId OR target_account_id = :accountId) " +
           "AND initiated_at BETWEEN :startDate AND :endDate " +
           "ORDER BY initiated_at DESC")
    Flux<Transaction> findByAccountAndDateRange(
        UUID accountId, Instant startDate, Instant endDate);

    /**
     * Find pending review transactions.
     */
    @Query("SELECT * FROM transactions WHERE status = 'PENDING_REVIEW' ORDER BY initiated_at ASC")
    Flux<Transaction> findPendingReviewTransactions();

    /**
     * Count transactions by status.
     */
    Mono<Long> countByStatus(TransactionStatus status);
}
