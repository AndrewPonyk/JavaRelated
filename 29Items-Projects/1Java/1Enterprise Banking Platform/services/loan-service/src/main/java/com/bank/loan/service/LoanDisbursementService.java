package com.bank.loan.service;

import com.bank.loan.dto.LoanResponse;
import com.bank.loan.model.Loan;
import com.bank.loan.model.Loan.LoanStatus;
import com.bank.loan.model.LoanApplication;
import com.bank.loan.model.LoanApplication.ApplicationStatus;
import com.bank.loan.model.LoanPayment;
import com.bank.loan.model.LoanPayment.PaymentStatus;
import com.bank.loan.repository.LoanApplicationRepository;
import com.bank.loan.repository.LoanPaymentRepository;
import com.bank.loan.repository.LoanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service for loan disbursement and management.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoanDisbursementService {

    private final LoanRepository loanRepository;
    private final LoanApplicationRepository applicationRepository;
    private final LoanPaymentRepository paymentRepository;
    private final AccountServiceClient accountServiceClient;

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Disburse an approved loan.
     */
    @Transactional
    public Mono<LoanResponse> disburseLoan(UUID applicationId, UUID disbursementAccountId) {
        log.info("Disbursing loan for application: {} to account: {}", applicationId, disbursementAccountId);

        return applicationRepository.findById(applicationId)
            .switchIfEmpty(Mono.error(new LoanApplicationService.ApplicationNotFoundException(applicationId)))
            .flatMap(application -> {
                if (application.getStatus() != ApplicationStatus.APPROVED) {
                    return Mono.error(new IllegalStateException(
                        "Can only disburse approved applications. Current status: " + application.getStatus()));
                }

                // Create the loan
                Loan loan = Loan.builder()
                    .id(UUID.randomUUID())
                    .loanNumber(generateLoanNumber())
                    .applicationId(applicationId)
                    .borrowerId(application.getApplicantId())
                    .borrowerName(application.getApplicantName())
                    .disbursementAccountId(disbursementAccountId)
                    .loanType(application.getLoanType())
                    .principalAmount(application.getApprovedAmount())
                    .currency(application.getCurrency())
                    .interestRate(application.getInterestRate())
                    .termMonths(application.getTermMonths())
                    .monthlyPayment(application.getMonthlyPayment())
                    .outstandingPrincipal(application.getApprovedAmount())
                    .outstandingInterest(BigDecimal.ZERO)
                    .totalPaid(BigDecimal.ZERO)
                    .status(LoanStatus.ACTIVE)
                    .startDate(LocalDate.now())
                    .endDate(LocalDate.now().plusMonths(application.getTermMonths()))
                    .nextPaymentDue(LocalDate.now().plusMonths(1))
                    .paymentsMade(0)
                    .paymentsMissed(0)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

                return loanRepository.save(loan)
                    .flatMap(savedLoan ->
                        // Transfer funds to disbursement account
                        accountServiceClient.deposit(
                            disbursementAccountId,
                            savedLoan.getPrincipalAmount(),
                            savedLoan.getCurrency(),
                            "LOAN-DISBURSEMENT-" + savedLoan.getLoanNumber()
                        )
                        .flatMap(result -> {
                            if (!result.isSuccess()) {
                                return Mono.error(new DisbursementException(
                                    "Failed to transfer funds: " + result.getErrorMessage()));
                            }
                            return Mono.just(savedLoan);
                        })
                    )
                    .flatMap(savedLoan ->
                        // Create payment schedule
                        createPaymentSchedule(savedLoan)
                            .then(Mono.just(savedLoan))
                    )
                    .flatMap(savedLoan -> {
                        // Update application status
                        application.setStatus(ApplicationStatus.DISBURSED);
                        application.setDisbursedAt(Instant.now());
                        return applicationRepository.save(application)
                            .thenReturn(savedLoan);
                    });
            })
            .map(this::mapToResponse);
    }

    /**
     * Get loan by ID.
     */
    public Mono<LoanResponse> getLoan(UUID loanId) {
        return loanRepository.findById(loanId)
            .switchIfEmpty(Mono.error(new LoanNotFoundException(loanId)))
            .map(this::mapToResponse);
    }

    /**
     * Get loan by number.
     */
    public Mono<LoanResponse> getLoanByNumber(String loanNumber) {
        return loanRepository.findByLoanNumber(loanNumber)
            .switchIfEmpty(Mono.error(new LoanNotFoundException(loanNumber)))
            .map(this::mapToResponse);
    }

    /**
     * Get loans by borrower.
     */
    public Flux<LoanResponse> getLoansByBorrower(UUID borrowerId) {
        return loanRepository.findByBorrowerId(borrowerId)
            .map(this::mapToResponse);
    }

    /**
     * Get payment schedule.
     */
    public Flux<LoanPayment> getPaymentSchedule(UUID loanId) {
        return paymentRepository.findPaymentSchedule(loanId);
    }

    /**
     * Make a payment.
     */
    @Transactional
    public Mono<LoanPayment> makePayment(UUID loanId, BigDecimal amount, String paymentMethod,
                                         UUID sourceAccountId) {
        log.info("Processing payment of {} for loan {}", amount, loanId);

        return loanRepository.findById(loanId)
            .switchIfEmpty(Mono.error(new LoanNotFoundException(loanId)))
            .flatMap(loan -> {
                if (loan.getStatus() != LoanStatus.ACTIVE && loan.getStatus() != LoanStatus.DELINQUENT) {
                    return Mono.error(new IllegalStateException(
                        "Cannot make payment on loan in status: " + loan.getStatus()));
                }

                return paymentRepository.findNextPayment(loanId)
                    .flatMap(payment -> {
                        // Withdraw from source account
                        return accountServiceClient.withdraw(
                            sourceAccountId,
                            amount,
                            loan.getCurrency(),
                            "LOAN-PAYMENT-" + loan.getLoanNumber()
                        )
                        .flatMap(result -> {
                            if (!result.isSuccess()) {
                                return Mono.error(new PaymentException(
                                    "Failed to withdraw funds: " + result.getErrorMessage()));
                            }

                            // Calculate principal and interest split
                            BigDecimal interestPortion = calculateInterestPortion(loan, amount);
                            BigDecimal principalPortion = amount.subtract(interestPortion);

                            payment.setPrincipalPaid(principalPortion);
                            payment.setInterestPaid(interestPortion);
                            payment.setStatus(amount.compareTo(payment.getTotalAmount()) >= 0
                                ? PaymentStatus.PAID : PaymentStatus.PARTIAL);
                            payment.setPaymentDate(Instant.now());
                            payment.setPaymentMethod(paymentMethod);
                            payment.setTransactionReference("PMT-" + UUID.randomUUID().toString().substring(0, 8));

                            // Update loan
                            loan.recordPayment(principalPortion, interestPortion);

                            return Mono.zip(
                                paymentRepository.save(payment),
                                loanRepository.save(loan)
                            ).map(tuple -> tuple.getT1());
                        });
                    });
            });
    }

    /**
     * Get delinquent loans.
     */
    public Flux<LoanResponse> getDelinquentLoans() {
        return loanRepository.findDelinquentLoans()
            .map(this::mapToResponse);
    }

    /**
     * Get loan statistics.
     */
    public Mono<LoanStatistics> getStatistics() {
        return Mono.zip(
            loanRepository.countByStatus(LoanStatus.ACTIVE),
            loanRepository.countByStatus(LoanStatus.DELINQUENT),
            loanRepository.countByStatus(LoanStatus.PAID_OFF),
            loanRepository.getTotalOutstandingPrincipal().defaultIfEmpty(BigDecimal.ZERO)
        ).map(tuple -> LoanStatistics.builder()
            .activeLoans(tuple.getT1())
            .delinquentLoans(tuple.getT2())
            .paidOffLoans(tuple.getT3())
            .totalOutstanding(tuple.getT4())
            .build());
    }

    private Mono<Void> createPaymentSchedule(Loan loan) {
        List<LoanPayment> payments = new ArrayList<>();
        LocalDate dueDate = loan.getStartDate().plusMonths(1);
        BigDecimal remainingPrincipal = loan.getPrincipalAmount();
        BigDecimal monthlyRate = loan.getInterestRate().divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP);

        for (int i = 1; i <= loan.getTermMonths(); i++) {
            BigDecimal interestAmount = remainingPrincipal.multiply(monthlyRate)
                .setScale(2, RoundingMode.HALF_UP);
            BigDecimal principalAmount = loan.getMonthlyPayment().subtract(interestAmount);
            remainingPrincipal = remainingPrincipal.subtract(principalAmount);

            LoanPayment payment = LoanPayment.builder()
                .id(UUID.randomUUID())
                .loanId(loan.getId())
                .paymentNumber(i)
                .dueDate(dueDate)
                .principalAmount(principalAmount)
                .interestAmount(interestAmount)
                .totalAmount(loan.getMonthlyPayment())
                .status(PaymentStatus.SCHEDULED)
                .createdAt(Instant.now())
                .build();

            payments.add(payment);
            dueDate = dueDate.plusMonths(1);
        }

        return Flux.fromIterable(payments)
            .flatMap(paymentRepository::save)
            .then();
    }

    private BigDecimal calculateInterestPortion(Loan loan, BigDecimal paymentAmount) {
        BigDecimal monthlyRate = loan.getInterestRate().divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP);
        BigDecimal interest = loan.getOutstandingPrincipal().multiply(monthlyRate)
            .setScale(2, RoundingMode.HALF_UP);
        return interest.min(paymentAmount);
    }

    private String generateLoanNumber() {
        String randomPart = String.format("%010d", RANDOM.nextLong(10000000000L));
        return "LN-" + randomPart;
    }

    private LoanResponse mapToResponse(Loan loan) {
        return LoanResponse.builder()
            .id(loan.getId())
            .loanNumber(loan.getLoanNumber())
            .applicationId(loan.getApplicationId())
            .borrowerId(loan.getBorrowerId())
            .borrowerName(loan.getBorrowerName())
            .loanType(loan.getLoanType())
            .principalAmount(loan.getPrincipalAmount())
            .currency(loan.getCurrency())
            .interestRate(loan.getInterestRate())
            .termMonths(loan.getTermMonths())
            .monthlyPayment(loan.getMonthlyPayment())
            .outstandingPrincipal(loan.getOutstandingPrincipal())
            .outstandingInterest(loan.getOutstandingInterest())
            .totalPaid(loan.getTotalPaid())
            .nextPaymentDue(loan.getNextPaymentDue())
            .status(loan.getStatus())
            .startDate(loan.getStartDate())
            .endDate(loan.getEndDate())
            .paymentsMade(loan.getPaymentsMade())
            .paymentsMissed(loan.getPaymentsMissed())
            .createdAt(loan.getCreatedAt())
            .build();
    }

    @lombok.Data
    @lombok.Builder
    public static class LoanStatistics {
        private long activeLoans;
        private long delinquentLoans;
        private long paidOffLoans;
        private BigDecimal totalOutstanding;
    }

    public static class LoanNotFoundException extends RuntimeException {
        public LoanNotFoundException(UUID loanId) {
            super("Loan not found: " + loanId);
        }
        public LoanNotFoundException(String loanNumber) {
            super("Loan not found with number: " + loanNumber);
        }
    }

    public static class DisbursementException extends RuntimeException {
        public DisbursementException(String message) {
            super(message);
        }
    }

    public static class PaymentException extends RuntimeException {
        public PaymentException(String message) {
            super(message);
        }
    }
}
