package com.bank.account.service;

import com.bank.account.dto.AccountResponse;
import com.bank.account.dto.CreateAccountRequest;
import com.bank.account.dto.DepositRequest;
import com.bank.account.dto.WithdrawRequest;
import com.bank.account.event.*;
import com.bank.account.exception.AccountNotFoundException;
import com.bank.account.exception.DuplicateAccountException;
import com.bank.account.exception.InsufficientFundsException;
import com.bank.account.exception.InvalidAccountStateException;
import com.bank.account.model.Account;
import com.bank.account.model.AccountStatus;
import com.bank.account.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Service layer for account management operations.
 * Implements business logic and event sourcing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final AccountEventPublisher eventPublisher;
    private final AccountNumberGenerator accountNumberGenerator;

    /**
     * Create a new bank account.
     */
    @Transactional
    public Mono<AccountResponse> createAccount(CreateAccountRequest request) {
        log.info("Creating account for owner: {}", request.getOwnerEmail());

        return accountNumberGenerator.generate()
            .flatMap(accountNumber ->
                accountRepository.existsByAccountNumber(accountNumber)
                    .flatMap(exists -> {
                        if (exists) {
                            return Mono.error(new DuplicateAccountException(
                                "Account number already exists: " + accountNumber));
                        }

                        BigDecimal initialBalance = request.getInitialDeposit() != null
                            ? request.getInitialDeposit()
                            : BigDecimal.ZERO;

                        Account account = Account.builder()
                            .id(UUID.randomUUID())
                            .accountNumber(accountNumber)
                            .ownerName(request.getOwnerName())
                            .ownerEmail(request.getOwnerEmail())
                            .accountType(request.getAccountType())
                            .currency(request.getCurrency())
                            .balance(initialBalance)
                            .status(AccountStatus.ACTIVE)
                            .createdAt(Instant.now())
                            .updatedAt(Instant.now())
                            .build();

                        return accountRepository.save(account);
                    })
            )
            .flatMap(savedAccount -> publishAccountCreatedEvent(savedAccount)
                .thenReturn(savedAccount))
            .map(this::mapToResponse)
            .doOnSuccess(response -> log.info("Account created: {}", response.getAccountNumber()))
            .doOnError(error -> log.error("Failed to create account", error));
    }

    /**
     * Get account by ID.
     */
    public Mono<AccountResponse> getAccountById(UUID accountId) {
        log.debug("Fetching account by ID: {}", accountId);
        return accountRepository.findById(accountId)
            .switchIfEmpty(Mono.error(new AccountNotFoundException("Account not found: " + accountId)))
            .map(this::mapToResponse);
    }

    /**
     * Get account by account number.
     */
    public Mono<AccountResponse> getAccountByNumber(String accountNumber) {
        log.debug("Fetching account by number: {}", accountNumber);
        return accountRepository.findByAccountNumber(accountNumber)
            .switchIfEmpty(Mono.error(new AccountNotFoundException("Account not found: " + accountNumber)))
            .map(this::mapToResponse);
    }

    /**
     * Get all accounts for a user by email.
     */
    public Flux<AccountResponse> getAccountsByOwnerEmail(String email) {
        log.debug("Fetching accounts for owner: {}", email);
        return accountRepository.findByOwnerEmail(email)
            .map(this::mapToResponse);
    }

    /**
     * Get all active accounts.
     */
    public Flux<AccountResponse> getAllActiveAccounts() {
        log.debug("Fetching all active accounts");
        return accountRepository.findByStatus(AccountStatus.ACTIVE)
            .map(this::mapToResponse);
    }

    /**
     * Get account balance.
     */
    public Mono<BigDecimal> getBalance(UUID accountId) {
        return accountRepository.findById(accountId)
            .switchIfEmpty(Mono.error(new AccountNotFoundException("Account not found: " + accountId)))
            .map(Account::getBalance);
    }

    /**
     * Deposit funds into an account.
     */
    @Transactional
    public Mono<AccountResponse> deposit(UUID accountId, DepositRequest request) {
        log.info("Depositing {} {} to account {}", request.getAmount(), request.getCurrency(), accountId);

        return accountRepository.findById(accountId)
            .switchIfEmpty(Mono.error(new AccountNotFoundException("Account not found: " + accountId)))
            .flatMap(account -> {
                validateAccountActive(account);
                validateCurrency(account, request.getCurrency());

                account.credit(request.getAmount());
                return accountRepository.save(account);
            })
            .flatMap(account -> publishFundsDepositedEvent(account, request.getAmount(), request.getReference())
                .thenReturn(account))
            .map(this::mapToResponse)
            .doOnSuccess(response -> log.info("Deposit completed for account {}", accountId));
    }

    /**
     * Withdraw funds from an account.
     */
    @Transactional
    public Mono<AccountResponse> withdraw(UUID accountId, WithdrawRequest request) {
        log.info("Withdrawing {} {} from account {}", request.getAmount(), request.getCurrency(), accountId);

        return accountRepository.findById(accountId)
            .switchIfEmpty(Mono.error(new AccountNotFoundException("Account not found: " + accountId)))
            .flatMap(account -> {
                validateAccountActive(account);
                validateCurrency(account, request.getCurrency());
                validateSufficientFunds(account, request.getAmount());

                account.debit(request.getAmount());
                return accountRepository.save(account);
            })
            .flatMap(account -> publishFundsWithdrawnEvent(account, request.getAmount(), request.getReference())
                .thenReturn(account))
            .map(this::mapToResponse)
            .doOnSuccess(response -> log.info("Withdrawal completed for account {}", accountId));
    }

    /**
     * Internal method to debit account for transfers.
     */
    @Transactional
    public Mono<Account> debitForTransfer(UUID accountId, BigDecimal amount, String transferReference) {
        log.info("Debiting {} for transfer {} from account {}", amount, transferReference, accountId);

        return accountRepository.findById(accountId)
            .switchIfEmpty(Mono.error(new AccountNotFoundException("Account not found: " + accountId)))
            .flatMap(account -> {
                validateAccountActive(account);
                validateSufficientFunds(account, amount);

                account.debit(amount);
                return accountRepository.save(account);
            })
            .flatMap(account -> publishFundsWithdrawnEvent(account, amount, transferReference)
                .thenReturn(account));
    }

    /**
     * Internal method to credit account for transfers.
     */
    @Transactional
    public Mono<Account> creditForTransfer(UUID accountId, BigDecimal amount, String transferReference) {
        log.info("Crediting {} for transfer {} to account {}", amount, transferReference, accountId);

        return accountRepository.findById(accountId)
            .switchIfEmpty(Mono.error(new AccountNotFoundException("Account not found: " + accountId)))
            .flatMap(account -> {
                validateAccountActive(account);

                account.credit(amount);
                return accountRepository.save(account);
            })
            .flatMap(account -> publishFundsDepositedEvent(account, amount, transferReference)
                .thenReturn(account));
    }

    /**
     * Freeze an account.
     */
    @Transactional
    public Mono<AccountResponse> freezeAccount(UUID accountId) {
        log.info("Freezing account: {}", accountId);

        return accountRepository.findById(accountId)
            .switchIfEmpty(Mono.error(new AccountNotFoundException("Account not found: " + accountId)))
            .flatMap(account -> {
                if (account.getStatus() == AccountStatus.CLOSED) {
                    return Mono.error(new InvalidAccountStateException("Cannot freeze a closed account"));
                }
                if (account.getStatus() == AccountStatus.FROZEN) {
                    return Mono.error(new InvalidAccountStateException("Account is already frozen"));
                }
                account.freeze();
                return accountRepository.save(account);
            })
            .flatMap(account -> publishAccountFrozenEvent(account).thenReturn(account))
            .map(this::mapToResponse);
    }

    /**
     * Unfreeze a frozen account.
     */
    @Transactional
    public Mono<AccountResponse> unfreezeAccount(UUID accountId) {
        log.info("Unfreezing account: {}", accountId);

        return accountRepository.findById(accountId)
            .switchIfEmpty(Mono.error(new AccountNotFoundException("Account not found: " + accountId)))
            .flatMap(account -> {
                if (account.getStatus() != AccountStatus.FROZEN) {
                    return Mono.error(new InvalidAccountStateException("Account is not frozen"));
                }
                account.unfreeze();
                return accountRepository.save(account);
            })
            .flatMap(account -> publishAccountUnfrozenEvent(account).thenReturn(account))
            .map(this::mapToResponse);
    }

    /**
     * Close an account.
     */
    @Transactional
    public Mono<AccountResponse> closeAccount(UUID accountId) {
        log.info("Closing account: {}", accountId);

        return accountRepository.findById(accountId)
            .switchIfEmpty(Mono.error(new AccountNotFoundException("Account not found: " + accountId)))
            .flatMap(account -> {
                if (account.getStatus() == AccountStatus.CLOSED) {
                    return Mono.error(new InvalidAccountStateException("Account is already closed"));
                }
                if (account.getBalance().compareTo(BigDecimal.ZERO) != 0) {
                    return Mono.error(new InvalidAccountStateException(
                        "Account balance must be zero to close. Current balance: " + account.getBalance()));
                }
                account.close();
                return accountRepository.save(account);
            })
            .flatMap(account -> publishAccountClosedEvent(account).thenReturn(account))
            .map(this::mapToResponse);
    }

    /**
     * Update account owner information.
     */
    @Transactional
    public Mono<AccountResponse> updateOwnerInfo(UUID accountId, String newName, String newEmail) {
        log.info("Updating owner info for account: {}", accountId);

        return accountRepository.findById(accountId)
            .switchIfEmpty(Mono.error(new AccountNotFoundException("Account not found: " + accountId)))
            .flatMap(account -> {
                if (newName != null && !newName.isBlank()) {
                    account.setOwnerName(newName);
                }
                if (newEmail != null && !newEmail.isBlank()) {
                    account.setOwnerEmail(newEmail);
                }
                account.setUpdatedAt(Instant.now());
                return accountRepository.save(account);
            })
            .map(this::mapToResponse);
    }

    // ==================== Validation Methods ====================

    private void validateAccountActive(Account account) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new InvalidAccountStateException(
                "Account is not active. Current status: " + account.getStatus());
        }
    }

    private void validateCurrency(Account account, String currency) {
        if (!account.getCurrency().equals(currency)) {
            throw new InvalidAccountStateException(
                "Currency mismatch. Account currency: " + account.getCurrency() + ", requested: " + currency);
        }
    }

    private void validateSufficientFunds(Account account, BigDecimal amount) {
        if (account.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException(
                "Insufficient funds. Available: " + account.getBalance() + ", requested: " + amount);
        }
    }

    // ==================== Event Publishing ====================

    private Mono<Void> publishAccountCreatedEvent(Account account) {
        AccountCreatedEvent event = AccountCreatedEvent.builder()
            .eventId(UUID.randomUUID())
            .accountId(account.getId())
            .accountNumber(account.getAccountNumber())
            .ownerEmail(account.getOwnerEmail())
            .accountType(account.getAccountType())
            .currency(account.getCurrency())
            .initialBalance(account.getBalance())
            .timestamp(Instant.now())
            .build();
        return eventPublisher.publish(event);
    }

    private Mono<Void> publishFundsDepositedEvent(Account account, BigDecimal amount, String reference) {
        FundsDepositedEvent event = FundsDepositedEvent.builder()
            .eventId(UUID.randomUUID())
            .accountId(account.getId())
            .amount(amount)
            .newBalance(account.getBalance())
            .reference(reference)
            .timestamp(Instant.now())
            .build();
        return eventPublisher.publish(event);
    }

    private Mono<Void> publishFundsWithdrawnEvent(Account account, BigDecimal amount, String reference) {
        FundsWithdrawnEvent event = FundsWithdrawnEvent.builder()
            .eventId(UUID.randomUUID())
            .accountId(account.getId())
            .amount(amount)
            .newBalance(account.getBalance())
            .reference(reference)
            .timestamp(Instant.now())
            .build();
        return eventPublisher.publish(event);
    }

    private Mono<Void> publishAccountFrozenEvent(Account account) {
        AccountFrozenEvent event = AccountFrozenEvent.builder()
            .eventId(UUID.randomUUID())
            .accountId(account.getId())
            .timestamp(Instant.now())
            .build();
        return eventPublisher.publish(event);
    }

    private Mono<Void> publishAccountUnfrozenEvent(Account account) {
        AccountUnfrozenEvent event = AccountUnfrozenEvent.builder()
            .eventId(UUID.randomUUID())
            .accountId(account.getId())
            .timestamp(Instant.now())
            .build();
        return eventPublisher.publish(event);
    }

    private Mono<Void> publishAccountClosedEvent(Account account) {
        AccountClosedEvent event = AccountClosedEvent.builder()
            .eventId(UUID.randomUUID())
            .accountId(account.getId())
            .timestamp(Instant.now())
            .build();
        return eventPublisher.publish(event);
    }

    // ==================== Mapping ====================

    private AccountResponse mapToResponse(Account account) {
        return AccountResponse.builder()
            .id(account.getId())
            .accountNumber(account.getAccountNumber())
            .ownerName(account.getOwnerName())
            .ownerEmail(account.getOwnerEmail())
            .accountType(account.getAccountType())
            .currency(account.getCurrency())
            .balance(account.getBalance())
            .status(account.getStatus())
            .createdAt(account.getCreatedAt())
            .updatedAt(account.getUpdatedAt())
            .build();
    }
}
