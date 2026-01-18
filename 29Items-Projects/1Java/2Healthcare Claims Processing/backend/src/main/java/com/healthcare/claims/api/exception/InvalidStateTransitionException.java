package com.healthcare.claims.api.exception;

import com.healthcare.claims.domain.model.ClaimStatus;

/**
 * Exception thrown when attempting an invalid claim state transition.
 */
public class InvalidStateTransitionException extends BusinessException {

    private final ClaimStatus currentStatus;
    private final ClaimStatus targetStatus;

    public InvalidStateTransitionException(ClaimStatus currentStatus, ClaimStatus targetStatus) {
        super("INVALID_STATE_TRANSITION",
            String.format("Cannot transition from %s to %s", currentStatus, targetStatus));
        this.currentStatus = currentStatus;
        this.targetStatus = targetStatus;
    }

    public ClaimStatus getCurrentStatus() {
        return currentStatus;
    }

    public ClaimStatus getTargetStatus() {
        return targetStatus;
    }
}
