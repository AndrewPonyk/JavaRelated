package com.healthcare.claims.infrastructure.kafka;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;

/**
 * Kafka consumer for fraud alert events.
 */
@ApplicationScoped
public class FraudAlertConsumer {

    private static final Logger LOG = Logger.getLogger(FraudAlertConsumer.class);

    /**
     * Consumes fraud alert events for notification and escalation.
     */
    @Incoming("fraud-alerts-in")
    public Uni<Void> processFraudAlert(Message<ClaimEventProducer.FraudEvent> message) {
        ClaimEventProducer.FraudEvent event = message.getPayload();

        LOG.warnf("Processing fraud alert for claim: %s, score: %.2f",
            event.claimNumber(), event.fraudScore());

        return handleFraudAlert(event)
            .chain(() -> Uni.createFrom().completionStage(message.ack()));
    }

    /**
     * Handles fraud alert - sends notifications and creates review tasks.
     */
    private Uni<Void> handleFraudAlert(ClaimEventProducer.FraudEvent event) {
        return Uni.createFrom().item(() -> {
            // TODO: Implement fraud alert handling:
            // 1. Send notification to fraud review team
            // 2. Create review task in case management system
            // 3. Log to audit trail
            // 4. Update dashboards/metrics

            LOG.infof("Fraud alert handled for claim: %s", event.claimNumber());
            LOG.infof("Fraud reasons: %s", event.fraudReasons());

            // Determine escalation level based on score
            String escalationLevel = determineEscalationLevel(event.fraudScore());
            LOG.infof("Escalation level: %s", escalationLevel);

            return null;
        });
    }

    /**
     * Determines the escalation level based on fraud score.
     */
    private String determineEscalationLevel(Double fraudScore) {
        if (fraudScore >= 0.9) {
            return "CRITICAL - Immediate SIU Review";
        } else if (fraudScore >= 0.8) {
            return "HIGH - Priority Fraud Review";
        } else if (fraudScore >= 0.7) {
            return "MEDIUM - Standard Fraud Review";
        } else {
            return "LOW - Monitor Only";
        }
    }
}
