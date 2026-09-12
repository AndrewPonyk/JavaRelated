package com.example.inventory.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "idempotency_records")
public class IdempotencyRecord {

    @Id
    @Column(name = "idempotency_key", length = 128)
    private String key;

    @Column(nullable = false, length = 80)
    private String operation;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "resource_id")
    private UUID resourceId;

    @Column(name = "response_body", nullable = false, columnDefinition = "json")
    private String responseBody;

    @Column(name = "http_status", nullable = false)
    private int httpStatus;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected IdempotencyRecord() {
    }

    public IdempotencyRecord(String key, String operation, String requestHash, UUID resourceId,
                             String responseBody, int httpStatus, Instant expiresAt) {
        this.key = key;
        this.operation = operation;
        this.requestHash = requestHash;
        this.resourceId = resourceId;
        this.responseBody = responseBody;
        this.httpStatus = httpStatus;
        this.expiresAt = expiresAt;
    }

    public String getKey() { return key; }
    public String getOperation() { return operation; }
    public String getRequestHash() { return requestHash; }
    public UUID getResourceId() { return resourceId; }
    public String getResponseBody() { return responseBody; }
    public int getHttpStatus() { return httpStatus; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
}

