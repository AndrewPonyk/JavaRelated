package com.bank.loan.repository;

import com.bank.loan.model.LoanPayment;
import com.bank.loan.model.LoanPayment.PaymentStatus;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Reactive repository for LoanPayment entity.
 */
@Repository
public interface LoanPaymentRepository extends R2dbcRepository<LoanPayment, UUID> {

    Flux<LoanPayment> findByLoanId(UUID loanId);

    Flux<LoanPayment> findByLoanIdAndStatus(UUID loanId, PaymentStatus status);

    @Query("SELECT * FROM loan_payments WHERE loan_id = :loanId ORDER BY payment_number ASC")
    Flux<LoanPayment> findPaymentSchedule(UUID loanId);

    @Query("SELECT * FROM loan_payments WHERE due_date <= :date AND status IN ('SCHEDULED', 'PENDING')")
    Flux<LoanPayment> findDuePayments(LocalDate date);

    @Query("SELECT * FROM loan_payments WHERE loan_id = :loanId AND status = 'SCHEDULED' ORDER BY due_date ASC LIMIT 1")
    Mono<LoanPayment> findNextPayment(UUID loanId);

    Mono<Long> countByLoanIdAndStatus(UUID loanId, PaymentStatus status);
}
