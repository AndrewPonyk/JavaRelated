package com.loanorigination.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class DocumentDto {
    private Long id;
    private Long applicationId;
    private String documentType;
    private String documentName;
    private Long fileSize;
    private String mimeType;
    private Long uploadedBy;
    private LocalDateTime uploadedAt;
    private boolean processed;
    private String downloadUrl;
}
