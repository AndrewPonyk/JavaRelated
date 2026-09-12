package com.ehrplatform.ml.controller;

import com.ehrplatform.ml.controller.StratificationDtos.LinkRequest;
import com.ehrplatform.ml.controller.StratificationDtos.LinkResponse;
import com.ehrplatform.ml.service.EntityLinkingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST API for ML entity linking (record linkage by note similarity). */
@RestController
@RequestMapping("/api/v1/link")
public class EntityLinkingController {

    private final EntityLinkingService entityLinkingService;

    public EntityLinkingController(EntityLinkingService entityLinkingService) {
        this.entityLinkingService = entityLinkingService;
    }

    @PostMapping
    public ResponseEntity<LinkResponse> link(@Valid @RequestBody LinkRequest request) {
        return ResponseEntity.ok(entityLinkingService.link(request));
    }
}
