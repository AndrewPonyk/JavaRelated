package com.bank.loan.repository;

import com.bank.loan.model.Loan;
import com.bank.loan.model.Loan.LoanStatus;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Reactive repository for Loan entity.
 */
@Repository
public interface LoanRepository extends R2dbcRepository<Loan, UUID> {

    Mono<Loan> findByLoanNumber(String loanNumber);

    Mono<Loan> findByApplicationId(UUID applicationId);

    Flux<Loan> findByBorrowerId(UUID borrowerId);

    Flux<Loan> findByStatus(LoanStatus status);

    @Query("SELECT * FROM loans WHERE next_payment_due <= :date AND status = 'ACTIVE'")
    Flux<Loan> findLoansDueByDate(LocalDate date);

    @Query("SELECT * FROM loans WHERE status IN ('DELINQUENT', 'DEFAULT') ORDER BY payments_missed DESC")
    Flux<Loan> findDelinquentLoans();

    @Query("SELECT SUM(outstanding_principal) FROM loans WHERE status = 'ACTIVE'")
    Mono<java.math.BigDecimal> getTotalOutstandingPrincipal();

    Mono<Long> countByStatus(LoanStatus status);
}
