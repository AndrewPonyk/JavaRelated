package com.loanorigination.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class DocumentSearchResultDto {
    private Long documentId;
    private Long applicationId;
    private String documentType;
    private String documentName;
    private String mimeType;
    private LocalDateTime uploadedAt;
    private String ocrTextSnippet;
    private double score;
}
