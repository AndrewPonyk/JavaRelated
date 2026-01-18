package com.healthcare.claims.domain.model;

/**
 * Represents the lifecycle states of a healthcare claim.
 */
public enum ClaimStatus {
    SUBMITTED,
    VALIDATING,
    INVALID,
    PENDING_ADJUDICATION,
    AUTO_ADJUDICATED,
    PENDING_REVIEW,
    FLAGGED_FRAUD,
    APPROVED,
    DENIED,
    PAID;

    /**
     * Checks if the claim can transition to the specified status.
     */
    public boolean canTransitionTo(ClaimStatus newStatus) {
        return switch (this) {
            case SUBMITTED -> newStatus == VALIDATING;
            case VALIDATING -> newStatus == INVALID || newStatus == PENDING_ADJUDICATION;
            case PENDING_ADJUDICATION -> newStatus == AUTO_ADJUDICATED ||
                                         newStatus == PENDING_REVIEW ||
                                         newStatus == FLAGGED_FRAUD;
            case AUTO_ADJUDICATED -> newStatus == APPROVED || newStatus == DENIED;
            case PENDING_REVIEW -> newStatus == APPROVED || newStatus == DENIED;
            case FLAGGED_FRAUD -> newStatus == PENDING_REVIEW || newStatus == DENIED;
            case APPROVED -> newStatus == PAID;
            case INVALID, DENIED, PAID -> false;
        };
    }

    /**
     * Checks if the claim is in a terminal state.
     */
    public boolean isTerminal() {
        return this == INVALID || this == DENIED || this == PAID;
    }

    /**
     * Checks if the claim requires human review.
     */
    public boolean requiresReview() {
        return this == PENDING_REVIEW || this == FLAGGED_FRAUD;
    }
}
