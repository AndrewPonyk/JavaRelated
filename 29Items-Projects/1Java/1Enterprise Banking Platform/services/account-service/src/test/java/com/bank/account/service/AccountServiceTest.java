package com.bank.account.service;

import com.bank.account.dto.AccountResponse;
import com.bank.account.dto.CreateAccountRequest;
import com.bank.account.dto.DepositRequest;
import com.bank.account.dto.WithdrawRequest;
import com.bank.account.event.AccountEventPublisher;
import com.bank.account.exception.AccountNotFoundException;
import com.bank.account.exception.InsufficientFundsException;
import com.bank.account.exception.InvalidAccountStateException;
import com.bank.account.model.Account;
import com.bank.account.model.AccountStatus;
import com.bank.account.model.AccountType;
import com.bank.account.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountService Tests")
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountEventPublisher eventPublisher;

    @Mock
    private AccountNumberGenerator accountNumberGenerator;

    @InjectMocks
    private AccountService accountService;

    private Account testAccount;
    private UUID accountId;

    @BeforeEach
    void setUp() {
        accountId = UUID.randomUUID();
        testAccount = Account.builder()
            .id(accountId)
            .accountNumber("2601-1234-5678")
            .ownerName("John Doe")
            .ownerEmail("john.doe@example.com")
            .accountType(AccountType.CHECKING)
            .currency("USD")
            .balance(new BigDecimal("1000.00"))
            .status(AccountStatus.ACTIVE)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    }

    @Nested
    @DisplayName("Create Account Tests")
    class CreateAccountTests {

        @Test
        @DisplayName("Should create account successfully")
        void shouldCreateAccountSuccessfully() {
            CreateAccountRequest request = CreateAccountRequest.builder()
                .ownerName("John Doe")
                .ownerEmail("john.doe@example.com")
                .accountType(AccountType.CHECKING)
                .currency("USD")
                .initialDeposit(new BigDecimal("500.00"))
                .build();

            when(accountNumberGenerator.generate()).thenReturn(Mono.just("2601-1234-5678"));
            when(accountRepository.existsByAccountNumber(any())).thenReturn(Mono.just(false));
            when(accountRepository.save(any(Account.class))).thenReturn(Mono.just(testAccount));
            when(eventPublisher.publish(any())).thenReturn(Mono.empty());

            StepVerifier.create(accountService.createAccount(request))
                .assertNext(response -> {
                    assertThat(response).isNotNull();
                    assertThat(response.getOwnerName()).isEqualTo("John Doe");
                    assertThat(response.getAccountNumber()).isEqualTo("2601-1234-5678");
                })
                .verifyComplete();

            verify(accountRepository).save(any(Account.class));
            verify(eventPublisher).publish(any());
        }

        @Test
        @DisplayName("Should create account with zero balance when no initial deposit")
        void shouldCreateAccountWithZeroBalance() {
            CreateAccountRequest request = CreateAccountRequest.builder()
                .ownerName("Jane Doe")
                .ownerEmail("jane.doe@example.com")
                .accountType(AccountType.SAVINGS)
                .currency("USD")
                .build();

            Account zeroBalanceAccount = testAccount.toBuilder()
                .balance(BigDecimal.ZERO)
                .build();

            when(accountNumberGenerator.generate()).thenReturn(Mono.just("2601-1234-5678"));
            when(accountRepository.existsByAccountNumber(any())).thenReturn(Mono.just(false));
            when(accountRepository.save(any(Account.class))).thenReturn(Mono.just(zeroBalanceAccount));
            when(eventPublisher.publish(any())).thenReturn(Mono.empty());

            StepVerifier.create(accountService.createAccount(request))
                .assertNext(response -> {
                    assertThat(response.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
                })
                .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Get Account Tests")
    class GetAccountTests {

        @Test
        @DisplayName("Should get account by ID")
        void shouldGetAccountById() {
            when(accountRepository.findById(accountId)).thenReturn(Mono.just(testAccount));

            StepVerifier.create(accountService.getAccountById(accountId))
                .assertNext(response -> {
                    assertThat(response.getId()).isEqualTo(accountId);
                    assertThat(response.getOwnerName()).isEqualTo("John Doe");
                })
                .verifyComplete();
        }

        @Test
        @DisplayName("Should throw exception when account not found")
        void shouldThrowExceptionWhenAccountNotFound() {
            when(accountRepository.findById(accountId)).thenReturn(Mono.empty());

            StepVerifier.create(accountService.getAccountById(accountId))
                .expectError(AccountNotFoundException.class)
                .verify();
        }

        @Test
        @DisplayName("Should get account by number")
        void shouldGetAccountByNumber() {
            when(accountRepository.findByAccountNumber("2601-1234-5678"))
                .thenReturn(Mono.just(testAccount));

            StepVerifier.create(accountService.getAccountByNumber("2601-1234-5678"))
                .assertNext(response -> {
                    assertThat(response.getAccountNumber()).isEqualTo("2601-1234-5678");
                })
                .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Deposit Tests")
    class DepositTests {

        @Test
        @DisplayName("Should deposit funds successfully")
        void shouldDepositFundsSuccessfully() {
            DepositRequest request = DepositRequest.builder()
                .amount(new BigDecimal("200.00"))
                .currency("USD")
                .reference("DEP-001")
                .build();

            Account updatedAccount = testAccount.toBuilder()
                .balance(new BigDecimal("1200.00"))
                .build();

            when(accountRepository.findById(accountId)).thenReturn(Mono.just(testAccount));
            when(accountRepository.save(any(Account.class))).thenReturn(Mono.just(updatedAccount));
            when(eventPublisher.publish(any())).thenReturn(Mono.empty());

            StepVerifier.create(accountService.deposit(accountId, request))
                .assertNext(response -> {
                    assertThat(response.getBalance()).isEqualByComparingTo(new BigDecimal("1200.00"));
                })
                .verifyComplete();
        }

        @Test
        @DisplayName("Should reject deposit to frozen account")
        void shouldRejectDepositToFrozenAccount() {
            testAccount.setStatus(AccountStatus.FROZEN);
            DepositRequest request = DepositRequest.builder()
                .amount(new BigDecimal("200.00"))
                .currency("USD")
                .build();

            when(accountRepository.findById(accountId)).thenReturn(Mono.just(testAccount));

            StepVerifier.create(accountService.deposit(accountId, request))
                .expectError(InvalidAccountStateException.class)
                .verify();
        }

        @Test
        @DisplayName("Should reject deposit with mismatched currency")
        void shouldRejectDepositWithMismatchedCurrency() {
            DepositRequest request = DepositRequest.builder()
                .amount(new BigDecimal("200.00"))
                .currency("EUR")
                .build();

            when(accountRepository.findById(accountId)).thenReturn(Mono.just(testAccount));

            StepVerifier.create(accountService.deposit(accountId, request))
                .expectError(InvalidAccountStateException.class)
                .verify();
        }
    }

    @Nested
    @DisplayName("Withdraw Tests")
    class WithdrawTests {

        @Test
        @DisplayName("Should withdraw funds successfully")
        void shouldWithdrawFundsSuccessfully() {
            WithdrawRequest request = WithdrawRequest.builder()
                .amount(new BigDecimal("300.00"))
                .currency("USD")
                .reference("WD-001")
                .build();

            Account updatedAccount = testAccount.toBuilder()
                .balance(new BigDecimal("700.00"))
                .build();

            when(accountRepository.findById(accountId)).thenReturn(Mono.just(testAccount));
            when(accountRepository.save(any(Account.class))).thenReturn(Mono.just(updatedAccount));
            when(eventPublisher.publish(any())).thenReturn(Mono.empty());

            StepVerifier.create(accountService.withdraw(accountId, request))
                .assertNext(response -> {
                    assertThat(response.getBalance()).isEqualByComparingTo(new BigDecimal("700.00"));
                })
                .verifyComplete();
        }

        @Test
        @DisplayName("Should reject withdrawal with insufficient funds")
        void shouldRejectWithdrawalWithInsufficientFunds() {
            WithdrawRequest request = WithdrawRequest.builder()
                .amount(new BigDecimal("2000.00"))
                .currency("USD")
                .build();

            when(accountRepository.findById(accountId)).thenReturn(Mono.just(testAccount));

            StepVerifier.create(accountService.withdraw(accountId, request))
                .expectError(InsufficientFundsException.class)
                .verify();
        }

        @Test
        @DisplayName("Should reject withdrawal from frozen account")
        void shouldRejectWithdrawalFromFrozenAccount() {
            testAccount.setStatus(AccountStatus.FROZEN);
            WithdrawRequest request = WithdrawRequest.builder()
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .build();

            when(accountRepository.findById(accountId)).thenReturn(Mono.just(testAccount));

            StepVerifier.create(accountService.withdraw(accountId, request))
                .expectError(InvalidAccountStateException.class)
                .verify();
        }
    }

    @Nested
    @DisplayName("Freeze/Unfreeze Tests")
    class FreezeUnfreezeTests {

        @Test
        @DisplayName("Should freeze account successfully")
        void shouldFreezeAccountSuccessfully() {
            Account frozenAccount = testAccount.toBuilder()
                .status(AccountStatus.FROZEN)
                .build();

            when(accountRepository.findById(accountId)).thenReturn(Mono.just(testAccount));
            when(accountRepository.save(any(Account.class))).thenReturn(Mono.just(frozenAccount));
            when(eventPublisher.publish(any())).thenReturn(Mono.empty());

            StepVerifier.create(accountService.freezeAccount(accountId))
                .assertNext(response -> {
                    assertThat(response.getStatus()).isEqualTo(AccountStatus.FROZEN);
                })
                .verifyComplete();
        }

        @Test
        @DisplayName("Should reject freeze on already frozen account")
        void shouldRejectFreezeOnAlreadyFrozenAccount() {
            testAccount.setStatus(AccountStatus.FROZEN);

            when(accountRepository.findById(accountId)).thenReturn(Mono.just(testAccount));

            StepVerifier.create(accountService.freezeAccount(accountId))
                .expectError(InvalidAccountStateException.class)
                .verify();
        }

        @Test
        @DisplayName("Should unfreeze account successfully")
        void shouldUnfreezeAccountSuccessfully() {
            testAccount.setStatus(AccountStatus.FROZEN);
            Account unfrozenAccount = testAccount.toBuilder()
                .status(AccountStatus.ACTIVE)
                .build();

            when(accountRepository.findById(accountId)).thenReturn(Mono.just(testAccount));
            when(accountRepository.save(any(Account.class))).thenReturn(Mono.just(unfrozenAccount));
            when(eventPublisher.publish(any())).thenReturn(Mono.empty());

            StepVerifier.create(accountService.unfreezeAccount(accountId))
                .assertNext(response -> {
                    assertThat(response.getStatus()).isEqualTo(AccountStatus.ACTIVE);
                })
                .verifyComplete();
        }

        @Test
        @DisplayName("Should reject unfreeze on non-frozen account")
        void shouldRejectUnfreezeOnNonFrozenAccount() {
            when(accountRepository.findById(accountId)).thenReturn(Mono.just(testAccount));

            StepVerifier.create(accountService.unfreezeAccount(accountId))
                .expectError(InvalidAccountStateException.class)
                .verify();
        }
    }

    @Nested
    @DisplayName("Close Account Tests")
    class CloseAccountTests {

        @Test
        @DisplayName("Should close account with zero balance")
        void shouldCloseAccountWithZeroBalance() {
            testAccount.setBalance(BigDecimal.ZERO);
            Account closedAccount = testAccount.toBuilder()
                .status(AccountStatus.CLOSED)
                .build();

            when(accountRepository.findById(accountId)).thenReturn(Mono.just(testAccount));
            when(accountRepository.save(any(Account.class))).thenReturn(Mono.just(closedAccount));
            when(eventPublisher.publish(any())).thenReturn(Mono.empty());

            StepVerifier.create(accountService.closeAccount(accountId))
                .assertNext(response -> {
                    assertThat(response.getStatus()).isEqualTo(AccountStatus.CLOSED);
                })
                .verifyComplete();
        }

        @Test
        @DisplayName("Should reject close with non-zero balance")
        void shouldRejectCloseWithNonZeroBalance() {
            when(accountRepository.findById(accountId)).thenReturn(Mono.just(testAccount));

            StepVerifier.create(accountService.closeAccount(accountId))
                .expectError(InvalidAccountStateException.class)
                .verify();
        }

        @Test
        @DisplayName("Should reject close on already closed account")
        void shouldRejectCloseOnAlreadyClosedAccount() {
            testAccount.setStatus(AccountStatus.CLOSED);
            testAccount.setBalance(BigDecimal.ZERO);

            when(accountRepository.findById(accountId)).thenReturn(Mono.just(testAccount));

            StepVerifier.create(accountService.closeAccount(accountId))
                .expectError(InvalidAccountStateException.class)
                .verify();
        }
    }

    @Nested
    @DisplayName("Get Balance Tests")
    class GetBalanceTests {

        @Test
        @DisplayName("Should get balance successfully")
        void shouldGetBalanceSuccessfully() {
            when(accountRepository.findById(accountId)).thenReturn(Mono.just(testAccount));

            StepVerifier.create(accountService.getBalance(accountId))
                .assertNext(balance -> {
                    assertThat(balance).isEqualByComparingTo(new BigDecimal("1000.00"));
                })
                .verifyComplete();
        }

        @Test
        @DisplayName("Should throw exception when account not found")
        void shouldThrowExceptionWhenAccountNotFound() {
            when(accountRepository.findById(accountId)).thenReturn(Mono.empty());

            StepVerifier.create(accountService.getBalance(accountId))
                .expectError(AccountNotFoundException.class)
                .verify();
        }
    }
}
