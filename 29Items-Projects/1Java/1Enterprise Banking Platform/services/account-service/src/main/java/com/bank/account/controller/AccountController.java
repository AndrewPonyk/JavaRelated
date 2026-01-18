package com.bank.account.controller;

import com.bank.account.dto.AccountResponse;
import com.bank.account.dto.CreateAccountRequest;
import com.bank.account.dto.DepositRequest;
import com.bank.account.dto.WithdrawRequest;
import com.bank.account.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * REST controller for account management operations.
 * Provides reactive endpoints for CRUD operations on accounts.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
@Tag(name = "Accounts", description = "Account management APIs")
public class AccountController {

    private final AccountService accountService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new account", description = "Creates a new bank account with the provided details")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Account created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "409", description = "Account already exists")
    })
    public Mono<AccountResponse> createAccount(
            @Valid @RequestBody CreateAccountRequest request) {
        log.info("Received create account request for: {}", request.getOwnerEmail());
        return accountService.createAccount(request);
    }

    @GetMapping("/{accountId}")
    @Operation(summary = "Get account by ID", description = "Retrieves account details by UUID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Account found"),
        @ApiResponse(responseCode = "404", description = "Account not found")
    })
    public Mono<AccountResponse> getAccountById(
            @Parameter(description = "Account UUID")
            @PathVariable UUID accountId) {
        log.debug("Fetching account: {}", accountId);
        return accountService.getAccountById(accountId);
    }

    @GetMapping("/number/{accountNumber}")
    @Operation(summary = "Get account by number", description = "Retrieves account details by account number")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Account found"),
        @ApiResponse(responseCode = "404", description = "Account not found")
    })
    public Mono<AccountResponse> getAccountByNumber(
            @Parameter(description = "Account number")
            @PathVariable String accountNumber) {
        log.debug("Fetching account by number: {}", accountNumber);
        return accountService.getAccountByNumber(accountNumber);
    }

    @GetMapping(value = "/owner/{email}", produces = MediaType.APPLICATION_NDJSON_VALUE)
    @Operation(summary = "Get accounts by owner", description = "Retrieves all accounts for a given owner email")
    public Flux<AccountResponse> getAccountsByOwner(
            @Parameter(description = "Owner email address")
            @PathVariable String email) {
        log.debug("Fetching accounts for owner: {}", email);
        return accountService.getAccountsByOwnerEmail(email);
    }

    @GetMapping("/{accountId}/balance")
    @Operation(summary = "Get account balance", description = "Retrieves the current balance for an account")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Balance retrieved"),
        @ApiResponse(responseCode = "404", description = "Account not found")
    })
    public Mono<BigDecimal> getBalance(
            @Parameter(description = "Account UUID")
            @PathVariable UUID accountId) {
        log.debug("Fetching balance for account: {}", accountId);
        return accountService.getBalance(accountId);
    }

    @PostMapping("/{accountId}/freeze")
    @Operation(summary = "Freeze account", description = "Freezes an account preventing any transactions")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Account frozen"),
        @ApiResponse(responseCode = "404", description = "Account not found")
    })
    public Mono<AccountResponse> freezeAccount(
            @Parameter(description = "Account UUID")
            @PathVariable UUID accountId) {
        log.info("Freezing account: {}", accountId);
        return accountService.freezeAccount(accountId);
    }

    @PostMapping("/{accountId}/unfreeze")
    @Operation(summary = "Unfreeze account", description = "Unfreezes a previously frozen account")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Account unfrozen"),
        @ApiResponse(responseCode = "400", description = "Account is not frozen"),
        @ApiResponse(responseCode = "404", description = "Account not found")
    })
    public Mono<AccountResponse> unfreezeAccount(
            @Parameter(description = "Account UUID")
            @PathVariable UUID accountId) {
        log.info("Unfreezing account: {}", accountId);
        return accountService.unfreezeAccount(accountId);
    }

    @PostMapping("/{accountId}/deposit")
    @Operation(summary = "Deposit funds", description = "Deposits funds into an account")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Deposit successful"),
        @ApiResponse(responseCode = "400", description = "Invalid request or account not active"),
        @ApiResponse(responseCode = "404", description = "Account not found")
    })
    public Mono<AccountResponse> deposit(
            @Parameter(description = "Account UUID")
            @PathVariable UUID accountId,
            @Valid @RequestBody DepositRequest request) {
        log.info("Depositing to account: {}", accountId);
        return accountService.deposit(accountId, request);
    }

    @PostMapping("/{accountId}/withdraw")
    @Operation(summary = "Withdraw funds", description = "Withdraws funds from an account")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Withdrawal successful"),
        @ApiResponse(responseCode = "400", description = "Invalid request, insufficient funds, or account not active"),
        @ApiResponse(responseCode = "404", description = "Account not found")
    })
    public Mono<AccountResponse> withdraw(
            @Parameter(description = "Account UUID")
            @PathVariable UUID accountId,
            @Valid @RequestBody WithdrawRequest request) {
        log.info("Withdrawing from account: {}", accountId);
        return accountService.withdraw(accountId, request);
    }

    @GetMapping(produces = MediaType.APPLICATION_NDJSON_VALUE)
    @Operation(summary = "Get all active accounts", description = "Retrieves all active accounts")
    public Flux<AccountResponse> getAllActiveAccounts() {
        log.debug("Fetching all active accounts");
        return accountService.getAllActiveAccounts();
    }

    @DeleteMapping("/{accountId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Close account", description = "Closes an account permanently (balance must be zero)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Account closed"),
        @ApiResponse(responseCode = "400", description = "Account has non-zero balance"),
        @ApiResponse(responseCode = "404", description = "Account not found")
    })
    public Mono<Void> closeAccount(
            @Parameter(description = "Account UUID")
            @PathVariable UUID accountId) {
        log.info("Closing account: {}", accountId);
        return accountService.closeAccount(accountId).then();
    }
}
