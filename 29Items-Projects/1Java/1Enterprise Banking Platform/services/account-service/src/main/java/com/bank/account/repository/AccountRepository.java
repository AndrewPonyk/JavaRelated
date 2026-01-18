package com.bank.account.repository;

import com.bank.account.model.Account;
import com.bank.account.model.AccountStatus;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Reactive repository for Account entity using R2DBC.
 */
@Repository
public interface AccountRepository extends R2dbcRepository<Account, UUID> {

    /**
     * Find account by account number.
     */
    Mono<Account> findByAccountNumber(String accountNumber);

    /**
     * Find all accounts by owner email.
     */
    Flux<Account> findByOwnerEmail(String ownerEmail);

    /**
     * Find all accounts by status.
     */
    Flux<Account> findByStatus(AccountStatus status);

    /**
     * Find active accounts by owner email.
     */
    @Query("SELECT * FROM accounts WHERE owner_email = :email AND status = 'ACTIVE'")
    Flux<Account> findActiveAccountsByOwnerEmail(String email);

    /**
     * Check if account number exists.
     */
    Mono<Boolean> existsByAccountNumber(String accountNumber);

    /**
     * Find accounts with balance above threshold.
     */
    @Query("SELECT * FROM accounts WHERE balance >= :threshold AND status = 'ACTIVE'")
    Flux<Account> findAccountsWithBalanceAbove(java.math.BigDecimal threshold);
}
