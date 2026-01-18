package com.bank.account.integration;

import com.bank.account.dto.AccountResponse;
import com.bank.account.dto.CreateAccountRequest;
import com.bank.account.dto.DepositRequest;
import com.bank.account.dto.WithdrawRequest;
import com.bank.account.model.AccountStatus;
import com.bank.account.model.AccountType;
import org.junit.Ignore;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Account Service Integration Tests")
@Ignore
@Disabled
class AccountServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
        DockerImageName.parse("postgres:15-alpine"))
        .withDatabaseName("accountdb")
        .withUsername("test")
        .withPassword("test");

    @Container
    static KafkaContainer kafka = new KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () ->
            String.format("r2dbc:postgresql://%s:%d/%s",
                postgres.getHost(),
                postgres.getFirstMappedPort(),
                postgres.getDatabaseName()));
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private WebTestClient webTestClient;

    private static UUID createdAccountId;

    @Test
    @Order(1)
    @DisplayName("Should create a new account")
    void shouldCreateNewAccount() {
        CreateAccountRequest request = CreateAccountRequest.builder()
            .ownerName("Integration Test User")
            .ownerEmail("integration.test@example.com")
            .accountType(AccountType.CHECKING)
            .currency("USD")
            .initialDeposit(new BigDecimal("1000.00"))
            .build();

        webTestClient.post()
            .uri("/api/v1/accounts")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isCreated()
            .expectBody(AccountResponse.class)
            .value(response -> {
                assertThat(response.getId()).isNotNull();
                assertThat(response.getAccountNumber()).isNotBlank();
                assertThat(response.getOwnerName()).isEqualTo("Integration Test User");
                assertThat(response.getBalance()).isEqualByComparingTo(new BigDecimal("1000.00"));
                assertThat(response.getStatus()).isEqualTo(AccountStatus.ACTIVE);
                createdAccountId = response.getId();
            });
    }

    @Test
    @Order(2)
    @DisplayName("Should get account by ID")
    void shouldGetAccountById() {
        assertThat(createdAccountId).isNotNull();

        webTestClient.get()
            .uri("/api/v1/accounts/{id}", createdAccountId)
            .exchange()
            .expectStatus().isOk()
            .expectBody(AccountResponse.class)
            .value(response -> {
                assertThat(response.getId()).isEqualTo(createdAccountId);
                assertThat(response.getOwnerName()).isEqualTo("Integration Test User");
            });
    }

    @Test
    @Order(3)
    @DisplayName("Should deposit funds")
    void shouldDepositFunds() {
        DepositRequest request = DepositRequest.builder()
            .amount(new BigDecimal("500.00"))
            .currency("USD")
            .reference("INT-DEP-001")
            .build();

        webTestClient.post()
            .uri("/api/v1/accounts/{id}/deposit", createdAccountId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isOk()
            .expectBody(AccountResponse.class)
            .value(response -> {
                assertThat(response.getBalance()).isEqualByComparingTo(new BigDecimal("1500.00"));
            });
    }

    @Test
    @Order(4)
    @DisplayName("Should withdraw funds")
    void shouldWithdrawFunds() {
        WithdrawRequest request = WithdrawRequest.builder()
            .amount(new BigDecimal("200.00"))
            .currency("USD")
            .reference("INT-WD-001")
            .build();

        webTestClient.post()
            .uri("/api/v1/accounts/{id}/withdraw", createdAccountId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isOk()
            .expectBody(AccountResponse.class)
            .value(response -> {
                assertThat(response.getBalance()).isEqualByComparingTo(new BigDecimal("1300.00"));
            });
    }

    @Test
    @Order(5)
    @DisplayName("Should reject withdrawal with insufficient funds")
    void shouldRejectWithdrawalWithInsufficientFunds() {
        WithdrawRequest request = WithdrawRequest.builder()
            .amount(new BigDecimal("5000.00"))
            .currency("USD")
            .build();

        webTestClient.post()
            .uri("/api/v1/accounts/{id}/withdraw", createdAccountId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.error.code").isEqualTo("INSUFFICIENT_FUNDS");
    }

    @Test
    @Order(6)
    @DisplayName("Should freeze account")
    void shouldFreezeAccount() {
        webTestClient.post()
            .uri("/api/v1/accounts/{id}/freeze", createdAccountId)
            .exchange()
            .expectStatus().isOk()
            .expectBody(AccountResponse.class)
            .value(response -> {
                assertThat(response.getStatus()).isEqualTo(AccountStatus.FROZEN);
            });
    }

    @Test
    @Order(7)
    @DisplayName("Should reject deposit to frozen account")
    void shouldRejectDepositToFrozenAccount() {
        DepositRequest request = DepositRequest.builder()
            .amount(new BigDecimal("100.00"))
            .currency("USD")
            .build();

        webTestClient.post()
            .uri("/api/v1/accounts/{id}/deposit", createdAccountId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.error.code").isEqualTo("INVALID_ACCOUNT_STATE");
    }

    @Test
    @Order(8)
    @DisplayName("Should unfreeze account")
    void shouldUnfreezeAccount() {
        webTestClient.post()
            .uri("/api/v1/accounts/{id}/unfreeze", createdAccountId)
            .exchange()
            .expectStatus().isOk()
            .expectBody(AccountResponse.class)
            .value(response -> {
                assertThat(response.getStatus()).isEqualTo(AccountStatus.ACTIVE);
            });
    }

    @Test
    @Order(9)
    @DisplayName("Should get account balance")
    void shouldGetAccountBalance() {
        webTestClient.get()
            .uri("/api/v1/accounts/{id}/balance", createdAccountId)
            .exchange()
            .expectStatus().isOk()
            .expectBody(BigDecimal.class)
            .value(balance -> {
                assertThat(balance).isEqualByComparingTo(new BigDecimal("1300.00"));
            });
    }

    @Test
    @Order(100)
    @DisplayName("Should return 404 for non-existent account")
    void shouldReturn404ForNonExistentAccount() {
        UUID nonExistentId = UUID.randomUUID();

        webTestClient.get()
            .uri("/api/v1/accounts/{id}", nonExistentId)
            .exchange()
            .expectStatus().isNotFound()
            .expectBody()
            .jsonPath("$.error.code").isEqualTo("ACCOUNT_NOT_FOUND");
    }
}
