package com.loanorigination.controller;

import com.loanorigination.dto.DocumentDto;
import com.loanorigination.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Tag(name = "Documents", description = "Endpoints for document management")
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a document for a loan application")
    @PreAuthorize("hasAnyRole('LOAN_OFFICER', 'ADMIN')")
    public ResponseEntity<DocumentDto> uploadDocument(
            @RequestParam("applicationId") Long applicationId,
            @RequestParam("documentType") String documentType,
            @RequestParam("file") MultipartFile file) {

        DocumentDto saved = documentService.uploadDocument(applicationId, documentType, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get document metadata by ID")
    @PreAuthorize("hasAnyRole('LOAN_OFFICER', 'UNDERWRITER', 'ADMIN')")
    public ResponseEntity<DocumentDto> getDocument(@PathVariable Long id) {
        return ResponseEntity.ok(documentService.getDocument(id));
    }

    @GetMapping("/{id}/download")
    @Operation(summary = "Download a document file")
    @PreAuthorize("hasAnyRole('LOAN_OFFICER', 'UNDERWRITER', 'ADMIN')")
    public ResponseEntity<Resource> downloadDocument(@PathVariable Long id) {
        DocumentDto meta = documentService.getDocument(id);
        Resource resource = documentService.downloadDocument(id);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        meta.getMimeType() != null ? meta.getMimeType() : "application/octet-stream"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + meta.getDocumentName() + "\"")
                .body(resource);
    }

    @GetMapping("/application/{applicationId}")
    @Operation(summary = "List all documents for a loan application")
    @PreAuthorize("hasAnyRole('LOAN_OFFICER', 'UNDERWRITER', 'ADMIN')")
    public ResponseEntity<List<DocumentDto>> listDocuments(
            @PathVariable Long applicationId) {
        return ResponseEntity.ok(documentService.listByApplicationId(applicationId));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a document")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteDocument(@PathVariable Long id) {
        documentService.deleteDocument(id);
        return ResponseEntity.noContent().build();
    }
}
