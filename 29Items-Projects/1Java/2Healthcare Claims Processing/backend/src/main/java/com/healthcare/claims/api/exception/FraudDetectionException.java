package com.healthcare.claims.api.exception;

import java.util.UUID;

/**
 * Exception thrown during fraud detection processing.
 */
public class FraudDetectionException extends BusinessException {

    private final UUID claimId;

    public FraudDetectionException(String message) {
        super("FRAUD_DETECTION_ERROR", message);
        this.claimId = null;
    }

    public FraudDetectionException(UUID claimId, String message) {
        super("FRAUD_DETECTION_ERROR", message);
        this.claimId = claimId;
    }

    public FraudDetectionException(UUID claimId, String message, Throwable cause) {
        super("FRAUD_DETECTION_ERROR", message, cause);
        this.claimId = claimId;
    }

    public UUID getClaimId() {
        return claimId;
    }
}
