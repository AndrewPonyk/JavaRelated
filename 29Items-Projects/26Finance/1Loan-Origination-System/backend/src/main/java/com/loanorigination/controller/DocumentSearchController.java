package com.loanorigination.controller;

import com.loanorigination.dto.DocumentSearchResultDto;
import com.loanorigination.service.DocumentSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/documents/search")
@RequiredArgsConstructor
@Tag(name = "Document Search", description = "Full-text search across loan documents")
public class DocumentSearchController {

    private final DocumentSearchService documentSearchService;

    @GetMapping
    @Operation(summary = "Search documents by text query")
    @PreAuthorize("hasAnyRole('LOAN_OFFICER', 'UNDERWRITER', 'ADMIN')")
    public ResponseEntity<List<DocumentSearchResultDto>> searchDocuments(
            @RequestParam("q") String query,
            @RequestParam(value = "applicationId", required = false) Long applicationId) {

        List<DocumentSearchResultDto> results = documentSearchService.search(query, applicationId);
        return ResponseEntity.ok(results);
    }
}
