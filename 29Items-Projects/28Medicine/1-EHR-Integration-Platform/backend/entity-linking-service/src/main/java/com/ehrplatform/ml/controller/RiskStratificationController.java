package com.ehrplatform.ml.controller;

import com.ehrplatform.ml.controller.StratificationDtos.StratificationRequest;
import com.ehrplatform.ml.controller.StratificationDtos.StratificationResponse;
import com.ehrplatform.ml.service.EntityLinkingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for risk stratification using similar clinical notes.
 */
@RestController
@RequestMapping("/api/v1/stratification")
public class RiskStratificationController {

    private final EntityLinkingService entityLinkingService;

    public RiskStratificationController(EntityLinkingService entityLinkingService) {
        this.entityLinkingService = entityLinkingService;
    }

    /**
     * {@code POST /api/v1/stratification} — score a patient against cohorts of
     * similar notes. Input is validated before reaching the service layer.
     */
    @PostMapping
    public ResponseEntity<StratificationResponse> stratify(
            @Valid @RequestBody StratificationRequest request) {
        return ResponseEntity.ok(entityLinkingService.stratify(request));
    }
}
