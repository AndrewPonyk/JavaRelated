package com.bank.fraud.service;

import com.bank.fraud.dto.FraudCheckRequest;
import com.bank.fraud.dto.FraudCheckResponse;
import com.bank.fraud.dto.FraudCheckResponse.RecommendedAction;
import com.bank.fraud.dto.FraudCheckResponse.RiskLevel;
import com.bank.fraud.ml.CatBoostModelService;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FraudDetectionService Tests")
class FraudDetectionServiceTest {

    @Mock
    private CatBoostModelService modelService;

    @Mock
    private FeatureEngineeringService featureService;

    @Mock
    private FraudAlertService alertService;

    @InjectMocks
    private FraudDetectionService fraudDetectionService;

    private FraudCheckRequest testRequest;
    private double[] testFeatures;

    @BeforeEach
    void setUp() {
        testRequest = FraudCheckRequest.builder()
            .transactionId(UUID.randomUUID())
            .sourceAccountId(UUID.randomUUID())
            .targetAccountId(UUID.randomUUID())
            .amount(new BigDecimal("500.00"))
            .currency("USD")
            .transactionType("TRANSFER")
            .build();

        testFeatures = new double[]{6.21, 0.0, 0.5, 0.4, 0.05, 0.1, 0.0, 0.0};
    }

    @Nested
    @DisplayName("Risk Score Analysis Tests")
    class RiskScoreAnalysisTests {

        @Test
        @DisplayName("Should return LOW risk for safe transactions")
        void shouldReturnLowRiskForSafeTransactions() {
            when(featureService.extractFeatures(any())).thenReturn(Mono.just(testFeatures));
            when(modelService.predict(any())).thenReturn(Mono.just(0.15));
            when(featureService.updateVelocity(any(), anyDouble())).thenReturn(Mono.empty());

            StepVerifier.create(fraudDetectionService.analyzeTransaction(testRequest))
                .assertNext(response -> {
                    assertThat(response.getRiskScore()).isLessThan(0.3);
                    assertThat(response.getRiskLevel()).isEqualTo(RiskLevel.LOW);
                    assertThat(response.getRecommendedAction()).isEqualTo(RecommendedAction.ALLOW);
                })
                .verifyComplete();

            verify(alertService, never()).createAlert(any(), any());
        }

        @Test
        @DisplayName("Should return MEDIUM risk and create alert")
        void shouldReturnMediumRiskAndCreateAlert() {
            when(featureService.extractFeatures(any())).thenReturn(Mono.just(testFeatures));
            when(modelService.predict(any())).thenReturn(Mono.just(0.45));
            when(alertService.createAlert(any(), any())).thenReturn(Mono.empty());

            StepVerifier.create(fraudDetectionService.analyzeTransaction(testRequest))
                .assertNext(response -> {
                    assertThat(response.getRiskScore()).isBetween(0.3, 0.6);
                    assertThat(response.getRiskLevel()).isEqualTo(RiskLevel.MEDIUM);
                })
                .verifyComplete();

            verify(alertService).createAlert(any(), any());
        }

        @Test
        @DisplayName("Should return HIGH risk with REVIEW action")
        void shouldReturnHighRiskWithReviewAction() {
            when(featureService.extractFeatures(any())).thenReturn(Mono.just(testFeatures));
            when(modelService.predict(any())).thenReturn(Mono.just(0.72));
            when(alertService.createAlert(any(), any())).thenReturn(Mono.empty());

            StepVerifier.create(fraudDetectionService.analyzeTransaction(testRequest))
                .assertNext(response -> {
                    assertThat(response.getRiskScore()).isBetween(0.6, 0.8);
                    assertThat(response.getRiskLevel()).isEqualTo(RiskLevel.HIGH);
                    assertThat(response.getRecommendedAction()).isEqualTo(RecommendedAction.REQUIRE_2FA);
                })
                .verifyComplete();
        }

        @Test
        @DisplayName("Should return CRITICAL risk with BLOCK action")
        void shouldReturnCriticalRiskWithBlockAction() {
            when(featureService.extractFeatures(any())).thenReturn(Mono.just(testFeatures));
            when(modelService.predict(any())).thenReturn(Mono.just(0.95));
            when(alertService.createAlert(any(), any())).thenReturn(Mono.empty());

            StepVerifier.create(fraudDetectionService.analyzeTransaction(testRequest))
                .assertNext(response -> {
                    assertThat(response.getRiskScore()).isGreaterThanOrEqualTo(0.8);
                    assertThat(response.getRiskLevel()).isEqualTo(RiskLevel.CRITICAL);
                    assertThat(response.getRecommendedAction()).isEqualTo(RecommendedAction.BLOCK);
                })
                .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Risk Factor Analysis Tests")
    class RiskFactorAnalysisTests {

        @Test
        @DisplayName("Should identify HIGH_AMOUNT risk factor")
        void shouldIdentifyHighAmountRiskFactor() {
            testRequest = testRequest.toBuilder()
                .amount(new BigDecimal("15000.00"))
                .build();

            when(featureService.extractFeatures(any())).thenReturn(Mono.just(testFeatures));
            when(modelService.predict(any())).thenReturn(Mono.just(0.55));
            when(alertService.createAlert(any(), any())).thenReturn(Mono.empty());

            StepVerifier.create(fraudDetectionService.analyzeTransaction(testRequest))
                .assertNext(response -> {
                    assertThat(response.getRiskFactors()).contains("HIGH_AMOUNT");
                })
                .verifyComplete();
        }

        @Test
        @DisplayName("Should identify VERY_HIGH_AMOUNT risk factor")
        void shouldIdentifyVeryHighAmountRiskFactor() {
            testRequest = testRequest.toBuilder()
                .amount(new BigDecimal("75000.00"))
                .build();

            when(featureService.extractFeatures(any())).thenReturn(Mono.just(testFeatures));
            when(modelService.predict(any())).thenReturn(Mono.just(0.65));
            when(alertService.createAlert(any(), any())).thenReturn(Mono.empty());

            StepVerifier.create(fraudDetectionService.analyzeTransaction(testRequest))
                .assertNext(response -> {
                    assertThat(response.getRiskFactors()).contains("VERY_HIGH_AMOUNT");
                })
                .verifyComplete();
        }

        @Test
        @DisplayName("Should identify EXTERNAL_TRANSFER risk factor")
        void shouldIdentifyExternalTransferRiskFactor() {
            testRequest = testRequest.toBuilder()
                .transactionType("EXTERNAL_TRANSFER")
                .build();

            when(featureService.extractFeatures(any())).thenReturn(Mono.just(testFeatures));
            when(modelService.predict(any())).thenReturn(Mono.just(0.35));
            when(alertService.createAlert(any(), any())).thenReturn(Mono.empty());

            StepVerifier.create(fraudDetectionService.analyzeTransaction(testRequest))
                .assertNext(response -> {
                    assertThat(response.getRiskFactors()).contains("EXTERNAL_TRANSFER");
                })
                .verifyComplete();
        }

        @Test
        @DisplayName("Should identify UNKNOWN_DEVICE risk factor")
        void shouldIdentifyUnknownDeviceRiskFactor() {
            testRequest = testRequest.toBuilder()
                .deviceId(null)
                .build();

            when(featureService.extractFeatures(any())).thenReturn(Mono.just(testFeatures));
            when(modelService.predict(any())).thenReturn(Mono.just(0.40));
            when(alertService.createAlert(any(), any())).thenReturn(Mono.empty());

            StepVerifier.create(fraudDetectionService.analyzeTransaction(testRequest))
                .assertNext(response -> {
                    assertThat(response.getRiskFactors()).contains("UNKNOWN_DEVICE");
                })
                .verifyComplete();
        }

        @Test
        @DisplayName("Should identify ML_MODEL_FLAG when score > 0.5")
        void shouldIdentifyMlModelFlag() {
            when(featureService.extractFeatures(any())).thenReturn(Mono.just(testFeatures));
            when(modelService.predict(any())).thenReturn(Mono.just(0.60));
            when(alertService.createAlert(any(), any())).thenReturn(Mono.empty());

            StepVerifier.create(fraudDetectionService.analyzeTransaction(testRequest))
                .assertNext(response -> {
                    assertThat(response.getRiskFactors()).contains("ML_MODEL_FLAG");
                })
                .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Performance Tests")
    class PerformanceTests {

        @Test
        @DisplayName("Should include inference time in response")
        void shouldIncludeInferenceTimeInResponse() {
            when(featureService.extractFeatures(any())).thenReturn(Mono.just(testFeatures));
            when(modelService.predict(any())).thenReturn(Mono.just(0.25));
            when(featureService.updateVelocity(any(), anyDouble())).thenReturn(Mono.empty());

            StepVerifier.create(fraudDetectionService.analyzeTransaction(testRequest))
                .assertNext(response -> {
                    assertThat(response.getInferenceTimeMs()).isGreaterThanOrEqualTo(0);
                    assertThat(response.getCheckedAt()).isNotNull();
                })
                .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle alert service failure gracefully")
        void shouldHandleAlertServiceFailureGracefully() {
            when(featureService.extractFeatures(any())).thenReturn(Mono.just(testFeatures));
            when(modelService.predict(any())).thenReturn(Mono.just(0.55));
            when(alertService.createAlert(any(), any()))
                .thenReturn(Mono.error(new RuntimeException("Alert service unavailable")));

            StepVerifier.create(fraudDetectionService.analyzeTransaction(testRequest))
                .assertNext(response -> {
                    assertThat(response).isNotNull();
                    assertThat(response.getRiskLevel()).isEqualTo(RiskLevel.MEDIUM);
                })
                .verifyComplete();
        }
    }
}
