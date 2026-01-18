package com.healthcare.claims.infrastructure.kafka;

import com.healthcare.claims.domain.service.ClaimService;
import com.healthcare.claims.infrastructure.elasticsearch.ClaimIndexer;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;

import java.util.UUID;

/**
 * Kafka consumer for processing claim events asynchronously.
 */
@ApplicationScoped
public class ClaimEventConsumer {

    private static final Logger LOG = Logger.getLogger(ClaimEventConsumer.class);

    @Inject
    ClaimService claimService;

    @Inject
    ClaimIndexer claimIndexer;

    /**
     * Consumes claim submitted events for async processing.
     */
    @Incoming("claim-events-in")
    public Uni<Void> processClaimEvent(Message<ClaimEventProducer.ClaimEvent> message) {
        ClaimEventProducer.ClaimEvent event = message.getPayload();
        LOG.infof("Received claim event: %s for claim: %s",
            event.eventType(), event.claimNumber());

        return switch (event.eventType()) {
            case "CLAIM_SUBMITTED" -> handleClaimSubmitted(event)
                .chain(() -> Uni.createFrom().completionStage(message.ack()));

            case "CLAIM_STATUS_CHANGED", "CLAIM_APPROVED", "CLAIM_DENIED" ->
                handleClaimUpdated(event)
                    .chain(() -> Uni.createFrom().completionStage(message.ack()));

            default -> {
                LOG.warnf("Unknown event type: %s", event.eventType());
                yield Uni.createFrom().completionStage(message.ack());
            }
        };
    }

    /**
     * Handles claim submitted event - triggers processing pipeline.
     */
    private Uni<Void> handleClaimSubmitted(ClaimEventProducer.ClaimEvent event) {
        UUID claimId = UUID.fromString(event.claimId());

        return claimService.processClaim(claimId)
            .chain(claim -> claimIndexer.indexClaim(claim))
            .onItem().invoke(v ->
                LOG.infof("Claim processing completed: %s", event.claimNumber()))
            .onFailure().invoke(e ->
                LOG.errorf(e, "Failed to process claim: %s", event.claimNumber()))
            .replaceWithVoid();
    }

    /**
     * Handles claim updated event - updates search index.
     */
    private Uni<Void> handleClaimUpdated(ClaimEventProducer.ClaimEvent event) {
        UUID claimId = UUID.fromString(event.claimId());

        return claimService.getClaimById(claimId)
            .chain(claim -> {
                if (claim != null) {
                    return claimIndexer.indexClaim(claim);
                }
                return Uni.createFrom().voidItem();
            })
            .onItem().invoke(v ->
                LOG.infof("Claim index updated: %s", event.claimNumber()))
            .onFailure().invoke(e ->
                LOG.errorf(e, "Failed to update claim index: %s", event.claimNumber()))
            .replaceWithVoid();
    }
}
