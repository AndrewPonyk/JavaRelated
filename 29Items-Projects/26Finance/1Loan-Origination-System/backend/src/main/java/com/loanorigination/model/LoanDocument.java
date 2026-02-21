package com.loanorigination.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "loan_document")
@Data
public class LoanDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "loan_document_seq")
    @SequenceGenerator(name = "loan_document_seq", sequenceName = "loan_document_seq", allocationSize = 1)
    private Long id;

    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    @Column(name = "document_type", nullable = false, length = 50)
    private String documentType;

    @Column(name = "document_name", nullable = false)
    private String documentName;

    @Column(name = "s3_key", nullable = false, length = 500)
    private String s3Key;

    @Column(name = "s3_bucket", nullable = false)
    private String s3Bucket;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    @Column(name = "uploaded_by")
    private Long uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "processed", length = 1)
    private boolean processed = false;

    @Column(name = "ocr_text", columnDefinition = "CLOB")
    private String ocrText;

    @PrePersist
    protected void onCreate() {
        if (uploadedAt == null) {
            uploadedAt = LocalDateTime.now();
        }
    }

    public void markProcessed() {
        this.processed = true;
    }
}
