package com.healthcare.claims.infrastructure.kafka;

import com.healthcare.claims.domain.model.Claim;
import io.smallrye.reactive.messaging.kafka.Record;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.jboss.logging.Logger;

import java.time.LocalDateTime;

/**
 * Kafka producer for claim-related events.
 */
@ApplicationScoped
public class ClaimEventProducer {

    private static final Logger LOG = Logger.getLogger(ClaimEventProducer.class);

    @Inject
    @Channel("claim-events-out")
    Emitter<Record<String, ClaimEvent>> claimEventEmitter;

    @Inject
    @Channel("fraud-events-out")
    Emitter<Record<String, FraudEvent>> fraudEventEmitter;

    /**
     * Sends a claim submitted event.
     */
    public void sendClaimSubmitted(Claim claim) {
        ClaimEvent event = new ClaimEvent(
            claim.getId().toString(),
            claim.getClaimNumber(),
            "CLAIM_SUBMITTED",
            claim.getStatus().name(),
            claim.getPatientId().toString(),
            claim.getProviderId().toString(),
            claim.getAmount().doubleValue(),
            LocalDateTime.now()
        );

        sendClaimEvent(event);
        LOG.infof("Published CLAIM_SUBMITTED event for claim: %s", claim.getClaimNumber());
    }

    /**
     * Sends a claim status changed event.
     */
    public void sendClaimStatusChanged(Claim claim) {
        ClaimEvent event = new ClaimEvent(
            claim.getId().toString(),
            claim.getClaimNumber(),
            "CLAIM_STATUS_CHANGED",
            claim.getStatus().name(),
            claim.getPatientId().toString(),
            claim.getProviderId().toString(),
            claim.getAmount().doubleValue(),
            LocalDateTime.now()
        );

        sendClaimEvent(event);
        LOG.infof("Published CLAIM_STATUS_CHANGED event for claim: %s to %s",
            claim.getClaimNumber(), claim.getStatus());
    }

    /**
     * Sends a claim approved event.
     */
    public void sendClaimApproved(Claim claim) {
        ClaimEvent event = new ClaimEvent(
            claim.getId().toString(),
            claim.getClaimNumber(),
            "CLAIM_APPROVED",
            claim.getStatus().name(),
            claim.getPatientId().toString(),
            claim.getProviderId().toString(),
            claim.getAllowedAmount() != null ?
                claim.getAllowedAmount().doubleValue() : claim.getAmount().doubleValue(),
            LocalDateTime.now()
        );

        sendClaimEvent(event);
        LOG.infof("Published CLAIM_APPROVED event for claim: %s", claim.getClaimNumber());
    }

    /**
     * Sends a claim denied event.
     */
    public void sendClaimDenied(Claim claim) {
        ClaimEvent event = new ClaimEvent(
            claim.getId().toString(),
            claim.getClaimNumber(),
            "CLAIM_DENIED",
            claim.getStatus().name(),
            claim.getPatientId().toString(),
            claim.getProviderId().toString(),
            claim.getAmount().doubleValue(),
            LocalDateTime.now()
        );

        sendClaimEvent(event);
        LOG.infof("Published CLAIM_DENIED event for claim: %s, reason: %s",
            claim.getClaimNumber(), claim.getDenialReason());
    }

    /**
     * Sends a fraud alert event.
     */
    public void sendFraudAlert(Claim claim) {
        FraudEvent event = new FraudEvent(
            claim.getId().toString(),
            claim.getClaimNumber(),
            claim.getFraudScore(),
            claim.getFraudReasons(),
            claim.getPatientId().toString(),
            claim.getProviderId().toString(),
            LocalDateTime.now()
        );

        fraudEventEmitter.send(Record.of(claim.getId().toString(), event));
        LOG.warnf("Published FRAUD_ALERT for claim: %s, score: %.2f",
            claim.getClaimNumber(), claim.getFraudScore());
    }

    private void sendClaimEvent(ClaimEvent event) {
        claimEventEmitter.send(Record.of(event.claimId(), event));
    }

    /**
     * Claim event payload.
     */
    public record ClaimEvent(
        String claimId,
        String claimNumber,
        String eventType,
        String status,
        String patientId,
        String providerId,
        Double amount,
        LocalDateTime timestamp
    ) {}

    /**
     * Fraud event payload.
     */
    public record FraudEvent(
        String claimId,
        String claimNumber,
        Double fraudScore,
        String fraudReasons,
        String patientId,
        String providerId,
        LocalDateTime timestamp
    ) {}
}
