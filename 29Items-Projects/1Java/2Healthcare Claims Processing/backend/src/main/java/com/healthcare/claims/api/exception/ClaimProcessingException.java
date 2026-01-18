package com.healthcare.claims.api.exception;

import java.util.UUID;

/**
 * Exception thrown during claim processing errors.
 */
public class ClaimProcessingException extends BusinessException {

    private final UUID claimId;
    private final String processingStage;

    public ClaimProcessingException(UUID claimId, String message) {
        super("CLAIM_PROCESSING_ERROR", message);
        this.claimId = claimId;
        this.processingStage = null;
    }

    public ClaimProcessingException(UUID claimId, String processingStage, String message) {
        super("CLAIM_PROCESSING_ERROR", message);
        this.claimId = claimId;
        this.processingStage = processingStage;
    }

    public ClaimProcessingException(UUID claimId, String processingStage, String message, Throwable cause) {
        super("CLAIM_PROCESSING_ERROR", message, cause);
        this.claimId = claimId;
        this.processingStage = processingStage;
    }

    public UUID getClaimId() {
        return claimId;
    }

    public String getProcessingStage() {
        return processingStage;
    }
}
