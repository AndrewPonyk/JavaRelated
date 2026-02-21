package com.loanorigination.service;

import com.loanorigination.dto.LoanApplicationDto;
import com.loanorigination.kafka.LoanEventProducer;
import com.loanorigination.model.LoanApplication;
import com.loanorigination.repository.ApplicantRepository;
import com.loanorigination.repository.LoanApplicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoanApplicationServiceTest {

    @Mock
    private LoanApplicationRepository repository;

    @Mock
    private ApplicantRepository applicantRepository;

    @Mock
    private LoanEventProducer eventProducer;

    @InjectMocks
    private LoanApplicationService service;

    private LoanApplicationDto validApplicationDto;

    @BeforeEach
    void setUp() {
        validApplicationDto = new LoanApplicationDto();
        validApplicationDto.setLoanAmount(new BigDecimal("50000"));
        validApplicationDto.setLoanPurpose("Home Purchase");
        validApplicationDto.setLoanTermMonths(360);
        validApplicationDto.setApplicantId(1L);
    }

    @Test
    void submitApplication_ValidData_ShouldSaveAndPublishEvent() {
        // Given
        LoanApplication savedApplication = new LoanApplication();
        savedApplication.setId(1L);
        savedApplication.setApplicationId("LA-12345");

        when(applicantRepository.existsById(1L)).thenReturn(true);
        when(repository.findByApplicantId(1L)).thenReturn(Collections.emptyList());
        when(repository.save(any(LoanApplication.class))).thenReturn(savedApplication);

        // When
        LoanApplication result = service.submitApplication(validApplicationDto);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        verify(repository, times(1)).save(any(LoanApplication.class));
        verify(eventProducer, times(1)).publishApplicationSubmitted(any(LoanApplication.class));
    }

    @Test
    void submitApplication_NullLoanAmount_ShouldThrowException() {
        // Given
        validApplicationDto.setLoanAmount(null);

        // When & Then
        assertThatThrownBy(() -> service.submitApplication(validApplicationDto))
                .isInstanceOf(LoanApplicationService.BusinessRuleException.class)
                .hasMessageContaining("Loan amount must be positive");

        verify(repository, never()).save(any());
        verify(eventProducer, never()).publishApplicationSubmitted(any());
    }

    @Test
    void submitApplication_NegativeLoanAmount_ShouldThrowException() {
        // Given
        validApplicationDto.setLoanAmount(new BigDecimal("-1000"));

        // When & Then
        assertThatThrownBy(() -> service.submitApplication(validApplicationDto))
                .isInstanceOf(LoanApplicationService.BusinessRuleException.class)
                .hasMessageContaining("Loan amount must be positive");
    }

    @Test
    void submitApplication_MissingApplicantId_ShouldThrowException() {
        // Given
        validApplicationDto.setApplicantId(null);

        // When & Then
        assertThatThrownBy(() -> service.submitApplication(validApplicationDto))
                .isInstanceOf(LoanApplicationService.BusinessRuleException.class)
                .hasMessageContaining("Applicant ID is required");
    }

    @Test
    void submitApplication_ApplicantNotFound_ShouldThrowException() {
        // Given
        when(applicantRepository.existsById(anyLong())).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> service.submitApplication(validApplicationDto))
                .isInstanceOf(LoanApplicationService.ResourceNotFoundException.class)
                .hasMessageContaining("Applicant not found");

        verify(repository, never()).save(any());
    }

    @Test
    void submitApplication_DuplicateActiveApplication_ShouldThrowException() {
        // Given
        LoanApplication active = new LoanApplication();
        active.setStatus(LoanApplication.ApplicationStatus.UNDER_REVIEW);

        when(applicantRepository.existsById(1L)).thenReturn(true);
        when(repository.findByApplicantId(1L)).thenReturn(Collections.singletonList(active));

        // When & Then
        assertThatThrownBy(() -> service.submitApplication(validApplicationDto))
                .isInstanceOf(LoanApplicationService.BusinessRuleException.class)
                .hasMessageContaining("already has an active loan application");

        verify(repository, never()).save(any());
    }

    @Test
    void getApplicationById_ExistingId_ShouldReturnApplication() {
        // Given
        Long applicationId = 1L;
        LoanApplication application = new LoanApplication();
        application.setId(applicationId);

        when(repository.findById(applicationId)).thenReturn(java.util.Optional.of(application));

        // When
        LoanApplication result = service.getApplicationById(applicationId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(applicationId);
        verify(repository, times(1)).findById(applicationId);
    }

    @Test
    void getApplicationById_NonExistingId_ShouldThrowException() {
        // Given
        Long applicationId = 999L;
        when(repository.findById(applicationId)).thenReturn(java.util.Optional.empty());

        // When & Then
        assertThatThrownBy(() -> service.getApplicationById(applicationId))
                .isInstanceOf(LoanApplicationService.ResourceNotFoundException.class)
                .hasMessageContaining("Application not found: 999");
    }

    @Test
    void updateStatus_InvalidTransition_ShouldThrowException() {
        // Given — SUBMITTED cannot go directly to APPROVED
        Long applicationId = 1L;
        LoanApplication application = new LoanApplication();
        application.setId(applicationId);
        application.setStatus(LoanApplication.ApplicationStatus.SUBMITTED);

        when(repository.findById(applicationId)).thenReturn(java.util.Optional.of(application));

        // When & Then
        assertThatThrownBy(() -> service.updateStatus(applicationId, "APPROVED"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid status transition");
    }
}
