package com.bank.loan.service;

import com.bank.loan.dto.LoanApplicationRequest;
import com.bank.loan.dto.LoanApplicationResponse;
import com.bank.loan.model.LoanApplication;
import com.bank.loan.model.LoanApplication.ApplicationStatus;
import com.bank.loan.model.LoanApplication.EmploymentStatus;
import com.bank.loan.model.LoanApplication.LoanType;
import com.bank.loan.repository.LoanApplicationRepository;
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
@DisplayName("LoanApplicationService Tests")
class LoanApplicationServiceTest {

    @Mock
    private LoanApplicationRepository applicationRepository;

    @Mock
    private CreditScoringService creditScoringService;

    @Mock
    private ApplicationNumberGenerator applicationNumberGenerator;

    @InjectMocks
    private LoanApplicationService loanApplicationService;

    private LoanApplication testApplication;
    private LoanApplicationRequest testRequest;
    private UUID applicationId;
    private UUID applicantId;

    @BeforeEach
    void setUp() {
        applicationId = UUID.randomUUID();
        applicantId = UUID.randomUUID();

        testRequest = LoanApplicationRequest.builder()
            .applicantId(applicantId)
            .applicantName("John Doe")
            .applicantEmail("john.doe@example.com")
            .loanType(LoanType.PERSONAL)
            .requestedAmount(new BigDecimal("25000.00"))
            .currency("USD")
            .termMonths(36)
            .annualIncome(new BigDecimal("75000.00"))
            .employmentStatus(EmploymentStatus.EMPLOYED)
            .purpose("Home improvement")
            .build();

        testApplication = LoanApplication.builder()
            .id(applicationId)
            .applicationNumber("LA-20260117-123456")
            .applicantId(applicantId)
            .applicantName("John Doe")
            .applicantEmail("john.doe@example.com")
            .loanType(LoanType.PERSONAL)
            .requestedAmount(new BigDecimal("25000.00"))
            .currency("USD")
            .termMonths(36)
            .annualIncome(new BigDecimal("75000.00"))
            .employmentStatus(EmploymentStatus.EMPLOYED)
            .purpose("Home improvement")
            .status(ApplicationStatus.SUBMITTED)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    }

    @Nested
    @DisplayName("Create Application Tests")
    class CreateApplicationTests {

        @Test
        @DisplayName("Should auto-approve application with excellent credit")
        void shouldAutoApproveWithExcellentCredit() {
            LoanApplication approvedApplication = testApplication.toBuilder()
                .status(ApplicationStatus.APPROVED)
                .creditScore(780)
                .approvedAmount(new BigDecimal("25000.00"))
                .reviewedBy("AUTOMATED_UNDERWRITING")
                .build();

            when(applicationNumberGenerator.generate()).thenReturn(Mono.just("LA-20260117-123456"));
            when(applicationRepository.save(any(LoanApplication.class)))
                .thenReturn(Mono.just(testApplication))
                .thenReturn(Mono.just(approvedApplication));
            when(creditScoringService.getCreditScore(applicantId)).thenReturn(Mono.just(780));

            StepVerifier.create(loanApplicationService.createApplication(testRequest))
                .assertNext(response -> {
                    assertThat(response.getStatus()).isEqualTo(ApplicationStatus.APPROVED);
                    assertThat(response.getCreditScore()).isEqualTo(780);
                    assertThat(response.getReviewedBy()).isEqualTo("AUTOMATED_UNDERWRITING");
                })
                .verifyComplete();
        }

        @Test
        @DisplayName("Should send for manual review with moderate credit")
        void shouldSendForManualReviewWithModerateCredit() {
            LoanApplication reviewApplication = testApplication.toBuilder()
                .status(ApplicationStatus.UNDER_REVIEW)
                .creditScore(680)
                .build();

            when(applicationNumberGenerator.generate()).thenReturn(Mono.just("LA-20260117-123456"));
            when(applicationRepository.save(any(LoanApplication.class)))
                .thenReturn(Mono.just(testApplication))
                .thenReturn(Mono.just(reviewApplication));
            when(creditScoringService.getCreditScore(applicantId)).thenReturn(Mono.just(680));

            StepVerifier.create(loanApplicationService.createApplication(testRequest))
                .assertNext(response -> {
                    assertThat(response.getStatus()).isEqualTo(ApplicationStatus.UNDER_REVIEW);
                    assertThat(response.getCreditScore()).isEqualTo(680);
                })
                .verifyComplete();
        }

        @Test
        @DisplayName("Should auto-reject with low credit score")
        void shouldAutoRejectWithLowCreditScore() {
            LoanApplication rejectedApplication = testApplication.toBuilder()
                .status(ApplicationStatus.REJECTED)
                .creditScore(280)
                .rejectionReason("Credit score below minimum threshold")
                .build();

            when(applicationNumberGenerator.generate()).thenReturn(Mono.just("LA-20260117-123456"));
            when(applicationRepository.save(any(LoanApplication.class)))
                .thenReturn(Mono.just(testApplication))
                .thenReturn(Mono.just(rejectedApplication));
            when(creditScoringService.getCreditScore(applicantId)).thenReturn(Mono.just(280));

            StepVerifier.create(loanApplicationService.createApplication(testRequest))
                .assertNext(response -> {
                    assertThat(response.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
                    assertThat(response.getRejectionReason()).contains("Credit score");
                })
                .verifyComplete();
        }

        @Test
        @DisplayName("Should auto-reject with high DTI ratio")
        void shouldAutoRejectWithHighDtiRatio() {
            // Very high loan amount relative to income
            testRequest = testRequest.toBuilder()
                .requestedAmount(new BigDecimal("500000.00"))
                .annualIncome(new BigDecimal("50000.00"))
                .build();

            LoanApplication rejectedApplication = testApplication.toBuilder()
                .status(ApplicationStatus.REJECTED)
                .creditScore(720)
                .rejectionReason("Debt-to-income ratio exceeds maximum allowed")
                .build();

            when(applicationNumberGenerator.generate()).thenReturn(Mono.just("LA-20260117-123456"));
            when(applicationRepository.save(any(LoanApplication.class)))
                .thenReturn(Mono.just(testApplication))
                .thenReturn(Mono.just(rejectedApplication));
            when(creditScoringService.getCreditScore(applicantId)).thenReturn(Mono.just(720));

            StepVerifier.create(loanApplicationService.createApplication(testRequest))
                .assertNext(response -> {
                    assertThat(response.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
                    assertThat(response.getRejectionReason()).contains("Debt-to-income");
                })
                .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Get Application Tests")
    class GetApplicationTests {

        @Test
        @DisplayName("Should get application by ID")
        void shouldGetApplicationById() {
            when(applicationRepository.findById(applicationId)).thenReturn(Mono.just(testApplication));

            StepVerifier.create(loanApplicationService.getApplication(applicationId))
                .assertNext(response -> {
                    assertThat(response.getId()).isEqualTo(applicationId);
                    assertThat(response.getApplicantName()).isEqualTo("John Doe");
                })
                .verifyComplete();
        }

        @Test
        @DisplayName("Should throw exception when application not found")
        void shouldThrowExceptionWhenApplicationNotFound() {
            when(applicationRepository.findById(applicationId)).thenReturn(Mono.empty());

            StepVerifier.create(loanApplicationService.getApplication(applicationId))
                .expectError(LoanApplicationService.ApplicationNotFoundException.class)
                .verify();
        }

        @Test
        @DisplayName("Should get applications by applicant")
        void shouldGetApplicationsByApplicant() {
            LoanApplication secondApplication = testApplication.toBuilder()
                .id(UUID.randomUUID())
                .applicationNumber("LA-20260117-654321")
                .loanType(LoanType.AUTO)
                .build();

            when(applicationRepository.findByApplicantId(applicantId))
                .thenReturn(Flux.just(testApplication, secondApplication));

            StepVerifier.create(loanApplicationService.getApplicationsByApplicant(applicantId))
                .expectNextCount(2)
                .verifyComplete();
        }

        @Test
        @DisplayName("Should get pending review applications")
        void shouldGetPendingReviewApplications() {
            testApplication.setStatus(ApplicationStatus.UNDER_REVIEW);

            when(applicationRepository.findPendingReview())
                .thenReturn(Flux.just(testApplication));

            StepVerifier.create(loanApplicationService.getPendingReviewApplications())
                .assertNext(response -> {
                    assertThat(response.getStatus()).isEqualTo(ApplicationStatus.UNDER_REVIEW);
                })
                .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Approve/Reject Tests")
    class ApproveRejectTests {

        @Test
        @DisplayName("Should approve application under review")
        void shouldApproveApplicationUnderReview() {
            testApplication.setStatus(ApplicationStatus.UNDER_REVIEW);

            LoanApplication approvedApplication = testApplication.toBuilder()
                .status(ApplicationStatus.APPROVED)
                .approvedAmount(new BigDecimal("20000.00"))
                .interestRate(new BigDecimal("0.08"))
                .reviewedBy("admin@bank.com")
                .approvedAt(Instant.now())
                .build();

            when(applicationRepository.findById(applicationId)).thenReturn(Mono.just(testApplication));
            when(applicationRepository.save(any(LoanApplication.class))).thenReturn(Mono.just(approvedApplication));

            StepVerifier.create(loanApplicationService.approveApplication(
                    applicationId, "admin@bank.com", new BigDecimal("20000.00"), new BigDecimal("0.08")))
                .assertNext(response -> {
                    assertThat(response.getStatus()).isEqualTo(ApplicationStatus.APPROVED);
                    assertThat(response.getApprovedAmount()).isEqualByComparingTo(new BigDecimal("20000.00"));
                    assertThat(response.getReviewedBy()).isEqualTo("admin@bank.com");
                })
                .verifyComplete();
        }

        @Test
        @DisplayName("Should reject approval for non-review status")
        void shouldRejectApprovalForNonReviewStatus() {
            testApplication.setStatus(ApplicationStatus.SUBMITTED);

            when(applicationRepository.findById(applicationId)).thenReturn(Mono.just(testApplication));

            StepVerifier.create(loanApplicationService.approveApplication(
                    applicationId, "admin@bank.com", new BigDecimal("20000.00"), new BigDecimal("0.08")))
                .expectError(IllegalStateException.class)
                .verify();
        }

        @Test
        @DisplayName("Should reject application under review")
        void shouldRejectApplicationUnderReview() {
            testApplication.setStatus(ApplicationStatus.UNDER_REVIEW);

            LoanApplication rejectedApplication = testApplication.toBuilder()
                .status(ApplicationStatus.REJECTED)
                .rejectionReason("Income verification failed")
                .reviewedBy("admin@bank.com")
                .build();

            when(applicationRepository.findById(applicationId)).thenReturn(Mono.just(testApplication));
            when(applicationRepository.save(any(LoanApplication.class))).thenReturn(Mono.just(rejectedApplication));

            StepVerifier.create(loanApplicationService.rejectApplication(
                    applicationId, "admin@bank.com", "Income verification failed"))
                .assertNext(response -> {
                    assertThat(response.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
                    assertThat(response.getRejectionReason()).isEqualTo("Income verification failed");
                })
                .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Cancel Application Tests")
    class CancelApplicationTests {

        @Test
        @DisplayName("Should cancel pending application")
        void shouldCancelPendingApplication() {
            LoanApplication cancelledApplication = testApplication.toBuilder()
                .status(ApplicationStatus.CANCELLED)
                .build();

            when(applicationRepository.findById(applicationId)).thenReturn(Mono.just(testApplication));
            when(applicationRepository.save(any(LoanApplication.class))).thenReturn(Mono.just(cancelledApplication));

            StepVerifier.create(loanApplicationService.cancelApplication(applicationId))
                .assertNext(response -> {
                    assertThat(response.getStatus()).isEqualTo(ApplicationStatus.CANCELLED);
                })
                .verifyComplete();
        }

        @Test
        @DisplayName("Should reject cancel for disbursed application")
        void shouldRejectCancelForDisbursedApplication() {
            testApplication.setStatus(ApplicationStatus.DISBURSED);

            when(applicationRepository.findById(applicationId)).thenReturn(Mono.just(testApplication));

            StepVerifier.create(loanApplicationService.cancelApplication(applicationId))
                .expectError(IllegalStateException.class)
                .verify();
        }
    }

    @Nested
    @DisplayName("Statistics Tests")
    class StatisticsTests {

        @Test
        @DisplayName("Should get application statistics")
        void shouldGetApplicationStatistics() {
            when(applicationRepository.countByStatus(ApplicationStatus.SUBMITTED)).thenReturn(Mono.just(10L));
            when(applicationRepository.countByStatus(ApplicationStatus.UNDER_REVIEW)).thenReturn(Mono.just(5L));
            when(applicationRepository.countByStatus(ApplicationStatus.APPROVED)).thenReturn(Mono.just(50L));
            when(applicationRepository.countByStatus(ApplicationStatus.REJECTED)).thenReturn(Mono.just(15L));
            when(applicationRepository.countByStatus(ApplicationStatus.DISBURSED)).thenReturn(Mono.just(45L));

            StepVerifier.create(loanApplicationService.getStatistics())
                .assertNext(stats -> {
                    assertThat(stats.getSubmitted()).isEqualTo(10L);
                    assertThat(stats.getUnderReview()).isEqualTo(5L);
                    assertThat(stats.getApproved()).isEqualTo(50L);
                    assertThat(stats.getRejected()).isEqualTo(15L);
                    assertThat(stats.getDisbursed()).isEqualTo(45L);
                })
                .verifyComplete();
        }
    }
}
