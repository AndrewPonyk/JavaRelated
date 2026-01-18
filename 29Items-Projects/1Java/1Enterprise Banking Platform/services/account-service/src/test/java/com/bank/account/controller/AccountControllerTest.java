package com.bank.account.controller;

import com.bank.account.dto.AccountResponse;
import com.bank.account.dto.CreateAccountRequest;
import com.bank.account.dto.DepositRequest;
import com.bank.account.dto.WithdrawRequest;
import com.bank.account.model.AccountStatus;
import com.bank.account.model.AccountType;
import com.bank.account.repository.AccountRepository;
import com.bank.account.service.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf;

@WebFluxTest(controllers = AccountController.class)
@ActiveProfiles("test")
@WithMockUser
@DisplayName("AccountController Tests")
class AccountControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private AccountService accountService;

    @MockBean
    private AccountRepository accountRepository;

    private AccountResponse testAccountResponse;
    private UUID accountId;

    @BeforeEach
    void setUp() {
        accountId = UUID.randomUUID();
        testAccountResponse = AccountResponse.builder()
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

    @Test
    @DisplayName("Create account successfully")
    void createAccountSuccessfully() {
        CreateAccountRequest request = CreateAccountRequest.builder()
            .ownerName("John Doe")
            .ownerEmail("john.doe@example.com")
            .accountType(AccountType.CHECKING)
            .currency("USD")
            .initialDeposit(new BigDecimal("500.00"))
            .build();

        when(accountService.createAccount(any(CreateAccountRequest.class)))
            .thenReturn(Mono.just(testAccountResponse));

        webTestClient.mutateWith(csrf()).post()
            .uri("/api/v1/accounts")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isCreated()
            .expectBody(AccountResponse.class)
            .value(response -> {
                assert response.getAccountNumber().equals("2601-1234-5678");
                assert response.getOwnerName().equals("John Doe");
                assert response.getStatus() == AccountStatus.ACTIVE;
            });
    }

    @Test
    @DisplayName("Get account by ID")
    void getAccountById() {
        when(accountService.getAccountById(accountId))
            .thenReturn(Mono.just(testAccountResponse));

        webTestClient.get()
            .uri("/api/v1/accounts/{id}", accountId)
            .exchange()
            .expectStatus().isOk()
            .expectBody(AccountResponse.class)
            .value(response -> {
                assert response.getId().equals(accountId);
                assert response.getOwnerName().equals("John Doe");
            });
    }

    @Test
    @DisplayName("Get account by account number")
    void getAccountByNumber() {
        when(accountService.getAccountByNumber("2601-1234-5678"))
            .thenReturn(Mono.just(testAccountResponse));

        webTestClient.get()
            .uri("/api/v1/accounts/number/{accountNumber}", "2601-1234-5678")
            .exchange()
            .expectStatus().isOk()
            .expectBody(AccountResponse.class)
            .value(response -> {
                assert response.getAccountNumber().equals("2601-1234-5678");
            });
    }

    @Test
    @DisplayName("Get account balance")
    void getAccountBalance() {
        when(accountService.getBalance(accountId))
            .thenReturn(Mono.just(new BigDecimal("1000.00")));

        webTestClient.get()
            .uri("/api/v1/accounts/{id}/balance", accountId)
            .exchange()
            .expectStatus().isOk()
            .expectBody(BigDecimal.class)
            .value(balance -> {
                assert balance.compareTo(new BigDecimal("1000.00")) == 0;
            });
    }

    @Test
    @DisplayName("Deposit funds")
    void depositFunds() {
        DepositRequest request = DepositRequest.builder()
            .amount(new BigDecimal("200.00"))
            .currency("USD")
            .reference("DEP-001")
            .build();

        AccountResponse updatedAccount = testAccountResponse.toBuilder()
            .balance(new BigDecimal("1200.00"))
            .build();

        when(accountService.deposit(eq(accountId), any(DepositRequest.class)))
            .thenReturn(Mono.just(updatedAccount));

        webTestClient.mutateWith(csrf()).post()
            .uri("/api/v1/accounts/{id}/deposit", accountId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isOk()
            .expectBody(AccountResponse.class)
            .value(response -> {
                assert response.getBalance().compareTo(new BigDecimal("1200.00")) == 0;
            });
    }

    @Test
    @DisplayName("Withdraw funds")
    void withdrawFunds() {
        WithdrawRequest request = WithdrawRequest.builder()
            .amount(new BigDecimal("300.00"))
            .currency("USD")
            .reference("WD-001")
            .build();

        AccountResponse updatedAccount = testAccountResponse.toBuilder()
            .balance(new BigDecimal("700.00"))
            .build();

        when(accountService.withdraw(eq(accountId), any(WithdrawRequest.class)))
            .thenReturn(Mono.just(updatedAccount));

        webTestClient.mutateWith(csrf()).post()
            .uri("/api/v1/accounts/{id}/withdraw", accountId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isOk()
            .expectBody(AccountResponse.class)
            .value(response -> {
                assert response.getBalance().compareTo(new BigDecimal("700.00")) == 0;
            });
    }

    @Test
    @DisplayName("Freeze account")
    void freezeAccount() {
        AccountResponse frozenAccount = testAccountResponse.toBuilder()
            .status(AccountStatus.FROZEN)
            .build();

        when(accountService.freezeAccount(accountId))
            .thenReturn(Mono.just(frozenAccount));

        webTestClient.mutateWith(csrf()).post()
            .uri("/api/v1/accounts/{id}/freeze", accountId)
            .exchange()
            .expectStatus().isOk()
            .expectBody(AccountResponse.class)
            .value(response -> {
                assert response.getStatus() == AccountStatus.FROZEN;
            });
    }

    @Test
    @DisplayName("Unfreeze account")
    void unfreezeAccount() {
        when(accountService.unfreezeAccount(accountId))
            .thenReturn(Mono.just(testAccountResponse));

        webTestClient.mutateWith(csrf()).post()
            .uri("/api/v1/accounts/{id}/unfreeze", accountId)
            .exchange()
            .expectStatus().isOk()
            .expectBody(AccountResponse.class)
            .value(response -> {
                assert response.getStatus() == AccountStatus.ACTIVE;
            });
    }

    @Test
    @DisplayName("Close account")
    void closeAccount() {
        AccountResponse closedAccount = testAccountResponse.toBuilder()
            .status(AccountStatus.CLOSED)
            .build();

        when(accountService.closeAccount(accountId))
            .thenReturn(Mono.just(closedAccount));

        webTestClient.mutateWith(csrf()).delete()
            .uri("/api/v1/accounts/{id}", accountId)
            .exchange()
            .expectStatus().isNoContent();
    }
}
