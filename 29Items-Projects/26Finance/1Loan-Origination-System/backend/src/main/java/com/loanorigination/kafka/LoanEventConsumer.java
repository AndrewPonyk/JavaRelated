package com.loanorigination.kafka;

import com.loanorigination.service.LoanApplicationService;
import com.loanorigination.service.UnderwritingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class LoanEventConsumer {

    private final UnderwritingService underwritingService;
    private final LoanApplicationService loanApplicationService;

    @KafkaListener(
        topics = "loan.application.events",
        groupId = "underwriting-service-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeApplicationEvent(
            @Payload Map<String, Object> event,
            Acknowledgment acknowledgment) {

        String eventType = (String) event.get("eventType");
        log.info("Received Kafka event of type: {}", eventType);

        try {
            if ("APPLICATION_SUBMITTED".equals(eventType)) {
                handleApplicationSubmitted(event);
            } else if ("APPLICATION_STATUS_CHANGED".equals(eventType)) {
                handleApplicationStatusChanged(event);
            } else {
                log.warn("Unrecognised event type '{}' — acknowledging and skipping", eventType);
            }

            // Only acknowledge after successful processing so the message is redelivered on failure
            acknowledgment.acknowledge();

        } catch (Exception e) {
            // Do NOT acknowledge — the message will be redelivered by the broker
            log.error("Failed to process Kafka event type='{}': {} — message will be redelivered",
                    eventType, e.getMessage(), e);
        }
    }

    private void handleApplicationSubmitted(Map<String, Object> event) {
        log.info("Processing APPLICATION_SUBMITTED event");

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) event.get("payload");
        if (payload == null || payload.get("id") == null) {
            log.warn("APPLICATION_SUBMITTED event has no payload.id — skipping");
            return;
        }

        Long applicationId = ((Number) payload.get("id")).longValue();
        log.info("Triggering underwriting for application: {}", applicationId);
        underwritingService.performUnderwriting(applicationId);
        log.info("Underwriting completed for application: {}", applicationId);
    }

    private void handleApplicationStatusChanged(Map<String, Object> event) {
        log.info("Processing APPLICATION_STATUS_CHANGED event");

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) event.get("payload");
        if (payload == null) {
            log.warn("APPLICATION_STATUS_CHANGED event has no payload — skipping");
            return;
        }

        Object idObj = payload.get("id");
        Object statusObj = payload.get("status");

        if (idObj == null || statusObj == null) {
            log.warn("APPLICATION_STATUS_CHANGED event missing id or status fields — skipping");
            return;
        }

        Long applicationId = ((Number) idObj).longValue();
        String newStatus = statusObj.toString();

        log.info("Applying status change: applicationId={} newStatus={}", applicationId, newStatus);
        loanApplicationService.updateStatus(applicationId, newStatus);
        log.info("Status change applied: applicationId={} newStatus={}", applicationId, newStatus);
    }
}
