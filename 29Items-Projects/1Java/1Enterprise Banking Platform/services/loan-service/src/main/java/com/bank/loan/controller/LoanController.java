package com.bank.loan.controller;

import com.bank.loan.dto.LoanResponse;
import com.bank.loan.model.LoanPayment;
import com.bank.loan.service.LoanDisbursementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
 * REST controller for loan management.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/loans")
@RequiredArgsConstructor
@Tag(name = "Loans", description = "Loan management APIs")
public class LoanController {

    private final LoanDisbursementService disbursementService;

    @PostMapping("/disburse")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Disburse loan", description = "Disburse an approved loan to an account")
    public Mono<LoanResponse> disburseLoan(
            @RequestParam UUID applicationId,
            @RequestParam UUID disbursementAccountId) {
        log.info("Disbursing loan for application: {} to account: {}", applicationId, disbursementAccountId);
        return disbursementService.disburseLoan(applicationId, disbursementAccountId);
    }

    @GetMapping("/{loanId}")
    @Operation(summary = "Get loan by ID", description = "Retrieve loan details")
    public Mono<LoanResponse> getLoan(@PathVariable UUID loanId) {
        log.debug("Fetching loan: {}", loanId);
        return disbursementService.getLoan(loanId);
    }

    @GetMapping("/number/{loanNumber}")
    @Operation(summary = "Get loan by number", description = "Retrieve loan by loan number")
    public Mono<LoanResponse> getLoanByNumber(@PathVariable String loanNumber) {
        log.debug("Fetching loan by number: {}", loanNumber);
        return disbursementService.getLoanByNumber(loanNumber);
    }

    @GetMapping(value = "/borrower/{borrowerId}", produces = MediaType.APPLICATION_NDJSON_VALUE)
    @Operation(summary = "Get borrower's loans", description = "Retrieve all loans for a borrower")
    public Flux<LoanResponse> getLoansByBorrower(@PathVariable UUID borrowerId) {
        log.debug("Fetching loans for borrower: {}", borrowerId);
        return disbursementService.getLoansByBorrower(borrowerId);
    }

    @GetMapping(value = "/{loanId}/schedule", produces = MediaType.APPLICATION_NDJSON_VALUE)
    @Operation(summary = "Get payment schedule", description = "Retrieve loan payment schedule")
    public Flux<LoanPayment> getPaymentSchedule(@PathVariable UUID loanId) {
        log.debug("Fetching payment schedule for loan: {}", loanId);
        return disbursementService.getPaymentSchedule(loanId);
    }

    @PostMapping("/{loanId}/pay")
    @Operation(summary = "Make payment", description = "Make a loan payment")
    public Mono<LoanPayment> makePayment(
            @PathVariable UUID loanId,
            @RequestParam BigDecimal amount,
            @RequestParam String paymentMethod,
            @RequestParam UUID sourceAccountId) {
        log.info("Processing payment of {} for loan {}", amount, loanId);
        return disbursementService.makePayment(loanId, amount, paymentMethod, sourceAccountId);
    }

    @GetMapping(value = "/delinquent", produces = MediaType.APPLICATION_NDJSON_VALUE)
    @Operation(summary = "Get delinquent loans", description = "Retrieve all delinquent loans")
    public Flux<LoanResponse> getDelinquentLoans() {
        log.debug("Fetching delinquent loans");
        return disbursementService.getDelinquentLoans();
    }

    @GetMapping("/stats")
    @Operation(summary = "Get loan statistics", description = "Retrieve loan portfolio statistics")
    public Mono<LoanDisbursementService.LoanStatistics> getStatistics() {
        log.debug("Fetching loan statistics");
        return disbursementService.getStatistics();
    }
}
