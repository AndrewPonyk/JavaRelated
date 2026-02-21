package com.loanorigination.controller;

import com.loanorigination.dto.LoanApplicationDto;
import com.loanorigination.model.LoanApplication;
import com.loanorigination.service.LoanApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
@Tag(name = "Loan Applications", description = "Endpoints for managing loan applications")
public class LoanApplicationController {

    private final LoanApplicationService loanApplicationService;

    @PostMapping
    @Operation(summary = "Submit a new loan application")
    @PreAuthorize("hasAnyRole('LOAN_OFFICER', 'ADMIN')")
    public ResponseEntity<LoanApplicationDto> submitApplication(
            @Valid @RequestBody LoanApplicationDto applicationDto) {
        
        LoanApplication savedApplication = loanApplicationService.submitApplication(applicationDto);
        LoanApplicationDto responseDto = mapToDto(savedApplication);
        return ResponseEntity.status(HttpStatus.CREATED).body(responseDto);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get application by ID")
    @PreAuthorize("hasAnyRole('LOAN_OFFICER', 'UNDERWRITER', 'ADMIN')")
    public ResponseEntity<LoanApplicationDto> getApplication(@PathVariable Long id) {
        LoanApplication application = loanApplicationService.getApplicationById(id);
        return ResponseEntity.ok(mapToDto(application));
    }

    @GetMapping
    @Operation(summary = "Get all applications")
    @PreAuthorize("hasAnyRole('LOAN_OFFICER', 'UNDERWRITER', 'ADMIN')")
    public ResponseEntity<List<LoanApplicationDto>> getAllApplications(
            @RequestParam(required = false) String status) {
        
        List<LoanApplication> applications = loanApplicationService.getAllApplications(status);
        List<LoanApplicationDto> responseDtos = applications.stream()
                .map(this::mapToDto)
                .toList();
        return ResponseEntity.ok(responseDtos);
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "Update application status")
    @PreAuthorize("hasAnyRole('UNDERWRITER', 'ADMIN')")
    public ResponseEntity<LoanApplicationDto> updateStatus(
            @PathVariable Long id,
            @RequestParam String status) {
        
        LoanApplication updatedApplication = loanApplicationService.updateStatus(id, status);
        return ResponseEntity.ok(mapToDto(updatedApplication));
    }

    private LoanApplicationDto mapToDto(LoanApplication application) {
        LoanApplicationDto dto = new LoanApplicationDto();
        dto.setId(application.getId());
        dto.setApplicationId(application.getApplicationId());
        dto.setLoanAmount(application.getLoanAmount());
        dto.setLoanPurpose(application.getLoanPurpose());
        dto.setLoanTermMonths(application.getLoanTermMonths());
        dto.setApplicantId(application.getApplicantId());
        dto.setStatus(application.getStatus().name());
        dto.setCreditScore(application.getCreditScore());
        dto.setDebtToIncomeRatio(application.getDebtToIncomeRatio());
        dto.setLoanToValueRatio(application.getLoanToValueRatio());
        dto.setCreatedAt(application.getCreatedAt());
        dto.setUpdatedAt(application.getUpdatedAt());
        dto.setUnderwritingDecision(application.getUnderwritingDecision());
        dto.setDecisionDate(application.getDecisionDate());
        return dto;
    }
}
