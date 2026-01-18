package com.bank.transaction.service;

import com.bank.transaction.dto.TransactionRequest;
import com.bank.transaction.dto.TransactionResponse;
import com.bank.transaction.model.Transaction;
import com.bank.transaction.model.TransactionStatus;
import com.bank.transaction.model.TransactionType;
import com.bank.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionService Tests")
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private FraudCheckClient fraudCheckClient;

    @Mock
    private AccountServiceClient accountServiceClient;

    @Mock
    private ReferenceNumberGenerator referenceGenerator;

    @InjectMocks
    private TransactionService transactionService;

    private Transaction testTransaction;
    private UUID transactionId;
    private UUID sourceAccountId;
    private UUID targetAccountId;

    @BeforeEach
    void setUp() {
        transactionId = UUID.randomUUID();
        sourceAccountId = UUID.randomUUID();
        targetAccountId = UUID.randomUUID();

        testTransaction = Transaction.builder()
            .id(transactionId)
            .referenceNumber("TXN-2601-12345678")
            .sourceAccountId(sourceAccountId)
            .targetAccountId(targetAccountId)
            .transactionType(TransactionType.INTERNAL_TRANSFER)
            .amount(new BigDecimal("500.00"))
            .currency("USD")
            .description("Test transfer")
            .status(TransactionStatus.INITIATED)
            .initiatedAt(Instant.now())
            .build();
    }

    @Nested
    @DisplayName("Initiate Transfer Tests")
    class InitiateTransferTests {

        @Test
        @DisplayName("Should initiate transfer successfully with low risk score")
        void shouldInitiateTransferWithLowRiskScore() {
            TransactionRequest request = TransactionRequest.builder()
                .sourceAccountId(sourceAccountId)
                .targetAccountId(targetAccountId)
                .transactionType(TransactionType.INTERNAL_TRANSFER)
                .amount(new BigDecimal("500.00"))
                .currency("USD")
                .description("Test transfer")
                .build();

            Transaction completedTransaction = testTransaction.toBuilder()
                .status(TransactionStatus.COMPLETED)
                .completedAt(Instant.now())
                .riskScore(0.2)
                .build();

            when(referenceGenerator.generate()).thenReturn(Mono.just("TXN-2601-12345678"));
            when(transactionRepository.save(any(Transaction.class)))
                .thenReturn(Mono.just(testTransaction))
                .thenReturn(Mono.just(completedTransaction));
            when(fraudCheckClient.checkFraud(any(Transaction.class))).thenReturn(Mono.just(0.2));
            when(accountServiceClient.debit(any(), any(), any(), any()))
                .thenReturn(Mono.just(new AccountServiceClient.AccountOperationResult(true, new BigDecimal("500.00"), null)));
            when(accountServiceClient.credit(any(), any(), any(), any()))
                .thenReturn(Mono.just(new AccountServiceClient.AccountOperationResult(true, new BigDecimal("1000.00"), null)));

            StepVerifier.create(transactionService.initiateTransfer(request))
                .assertNext(response -> {
                    assertThat(response.getReferenceNumber()).isEqualTo("TXN-2601-12345678");
                    assertThat(response.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
                })
                .verifyComplete();
        }

        @Test
        @DisplayName("Should flag transaction for review with high risk score")
        void shouldFlagTransactionForReviewWithHighRiskScore() {
            TransactionRequest request = TransactionRequest.builder()
                .sourceAccountId(sourceAccountId)
                .targetAccountId(targetAccountId)
                .transactionType(TransactionType.INTERNAL_TRANSFER)
                .amount(new BigDecimal("10000.00"))
                .currency("USD")
                .description("Large transfer")
                .build();

            Transaction flaggedTransaction = testTransaction.toBuilder()
                .status(TransactionStatus.PENDING_REVIEW)
                .riskScore(0.8)
                .build();

            when(referenceGenerator.generate()).thenReturn(Mono.just("TXN-2601-12345678"));
            when(transactionRepository.save(any(Transaction.class)))
                .thenReturn(Mono.just(testTransaction))
                .thenReturn(Mono.just(flaggedTransaction));
            when(fraudCheckClient.checkFraud(any(Transaction.class))).thenReturn(Mono.just(0.8));

            StepVerifier.create(transactionService.initiateTransfer(request))
                .assertNext(response -> {
                    assertThat(response.getStatus()).isEqualTo(TransactionStatus.PENDING_REVIEW);
                })
                .verifyComplete();

            verify(accountServiceClient, never()).debit(any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("Get Transaction Tests")
    class GetTransactionTests {

        @Test
        @DisplayName("Should get transaction by ID")
        void shouldGetTransactionById() {
            when(transactionRepository.findById(transactionId)).thenReturn(Mono.just(testTransaction));

            StepVerifier.create(transactionService.getTransaction(transactionId))
                .assertNext(response -> {
                    assertThat(response.getId()).isEqualTo(transactionId);
                    assertThat(response.getReferenceNumber()).isEqualTo("TXN-2601-12345678");
                })
                .verifyComplete();
        }

        @Test
        @DisplayName("Should throw exception when transaction not found")
        void shouldThrowExceptionWhenTransactionNotFound() {
            when(transactionRepository.findById(transactionId)).thenReturn(Mono.empty());

            StepVerifier.create(transactionService.getTransaction(transactionId))
                .expectError(TransactionService.TransactionNotFoundException.class)
                .verify();
        }

        @Test
        @DisplayName("Should get transactions by account")
        void shouldGetTransactionsByAccount() {
            Transaction transaction1 = testTransaction;
            Transaction transaction2 = testTransaction.toBuilder()
                .id(UUID.randomUUID())
                .referenceNumber("TXN-2601-87654321")
                .build();

            when(transactionRepository.findBySourceAccountIdOrTargetAccountId(sourceAccountId, sourceAccountId))
                .thenReturn(Flux.just(transaction1, transaction2));

            StepVerifier.create(transactionService.getTransactionsByAccount(sourceAccountId, 0, 10))
                .expectNextCount(2)
                .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Cancel Transaction Tests")
    class CancelTransactionTests {

        @Test
        @DisplayName("Should cancel pending transaction")
        void shouldCancelPendingTransaction() {
            Transaction cancelledTransaction = testTransaction.toBuilder()
                .status(TransactionStatus.CANCELLED)
                .build();

            when(transactionRepository.findById(transactionId)).thenReturn(Mono.just(testTransaction));
            when(transactionRepository.save(any(Transaction.class))).thenReturn(Mono.just(cancelledTransaction));

            StepVerifier.create(transactionService.cancelTransaction(transactionId))
                .assertNext(response -> {
                    assertThat(response.getStatus()).isEqualTo(TransactionStatus.CANCELLED);
                })
                .verifyComplete();
        }

        @Test
        @DisplayName("Should reject cancel for completed transaction")
        void shouldRejectCancelForCompletedTransaction() {
            testTransaction.setStatus(TransactionStatus.COMPLETED);

            when(transactionRepository.findById(transactionId)).thenReturn(Mono.just(testTransaction));

            StepVerifier.create(transactionService.cancelTransaction(transactionId))
                .expectError(IllegalStateException.class)
                .verify();
        }
    }

    @Nested
    @DisplayName("Approve Transaction Tests")
    class ApproveTransactionTests {

        @Test
        @DisplayName("Should approve pending review transaction")
        void shouldApprovePendingReviewTransaction() {
            testTransaction.setStatus(TransactionStatus.PENDING_REVIEW);

            Transaction completedTransaction = testTransaction.toBuilder()
                .status(TransactionStatus.COMPLETED)
                .completedAt(Instant.now())
                .build();

            when(transactionRepository.findById(transactionId)).thenReturn(Mono.just(testTransaction));
            when(transactionRepository.save(any(Transaction.class))).thenReturn(Mono.just(completedTransaction));
            when(accountServiceClient.debit(any(), any(), any(), any()))
                .thenReturn(Mono.just(new AccountServiceClient.AccountOperationResult(true, new BigDecimal("500.00"), null)));
            when(accountServiceClient.credit(any(), any(), any(), any()))
                .thenReturn(Mono.just(new AccountServiceClient.AccountOperationResult(true, new BigDecimal("1000.00"), null)));

            StepVerifier.create(transactionService.approveTransaction(transactionId))
                .assertNext(response -> {
                    assertThat(response.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
                })
                .verifyComplete();
        }

        @Test
        @DisplayName("Should reject approve for non-pending transaction")
        void shouldRejectApproveForNonPendingTransaction() {
            testTransaction.setStatus(TransactionStatus.COMPLETED);

            when(transactionRepository.findById(transactionId)).thenReturn(Mono.just(testTransaction));

            StepVerifier.create(transactionService.approveTransaction(transactionId))
                .expectError(IllegalStateException.class)
                .verify();
        }
    }

    @Nested
    @DisplayName("Reject Transaction Tests")
    class RejectTransactionTests {

        @Test
        @DisplayName("Should reject pending review transaction")
        void shouldRejectPendingReviewTransaction() {
            testTransaction.setStatus(TransactionStatus.PENDING_REVIEW);

            Transaction rejectedTransaction = testTransaction.toBuilder()
                .status(TransactionStatus.FAILED)
                .failureReason("Rejected: Suspicious activity")
                .build();

            when(transactionRepository.findById(transactionId)).thenReturn(Mono.just(testTransaction));
            when(transactionRepository.save(any(Transaction.class))).thenReturn(Mono.just(rejectedTransaction));

            StepVerifier.create(transactionService.rejectTransaction(transactionId, "Suspicious activity"))
                .assertNext(response -> {
                    assertThat(response.getStatus()).isEqualTo(TransactionStatus.FAILED);
                    assertThat(response.getFailureReason()).contains("Suspicious activity");
                })
                .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Statistics Tests")
    class StatisticsTests {

        @Test
        @DisplayName("Should get transaction statistics")
        void shouldGetTransactionStatistics() {
            when(transactionRepository.countByStatus(TransactionStatus.COMPLETED)).thenReturn(Mono.just(100L));
            when(transactionRepository.countByStatus(TransactionStatus.FAILED)).thenReturn(Mono.just(5L));
            when(transactionRepository.countByStatus(TransactionStatus.PENDING_REVIEW)).thenReturn(Mono.just(10L));
            when(transactionRepository.countByStatus(TransactionStatus.CANCELLED)).thenReturn(Mono.just(3L));

            StepVerifier.create(transactionService.getTransactionStats())
                .assertNext(stats -> {
                    assertThat(stats.getCompleted()).isEqualTo(100L);
                    assertThat(stats.getFailed()).isEqualTo(5L);
                    assertThat(stats.getPendingReview()).isEqualTo(10L);
                    assertThat(stats.getCancelled()).isEqualTo(3L);
                })
                .verifyComplete();
        }
    }
}
