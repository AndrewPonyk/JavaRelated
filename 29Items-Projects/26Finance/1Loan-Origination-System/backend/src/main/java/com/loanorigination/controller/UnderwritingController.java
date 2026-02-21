package com.loanorigination.controller;

import com.loanorigination.dto.UnderwritingResultDto;
import com.loanorigination.service.UnderwritingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/underwriting")
@RequiredArgsConstructor
@Tag(name = "Underwriting", description = "Endpoints for loan underwriting")
public class UnderwritingController {

    private final UnderwritingService underwritingService;

    @PostMapping("/{applicationId}")
    @Operation(summary = "Trigger underwriting for an application")
    @PreAuthorize("hasAnyRole('UNDERWRITER', 'ADMIN')")
    public ResponseEntity<UnderwritingResultDto> performUnderwriting(@PathVariable Long applicationId) {
        UnderwritingResultDto result = underwritingService.performUnderwriting(applicationId);
        return ResponseEntity.ok(result);
    }
}
