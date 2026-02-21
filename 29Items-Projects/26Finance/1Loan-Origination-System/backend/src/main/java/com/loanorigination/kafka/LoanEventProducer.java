package com.loanorigination.kafka;

import com.loanorigination.model.LoanApplication;
import com.loanorigination.model.LoanDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class LoanEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String TOPIC_APPLICATION_EVENTS = "loan.application.events";
    private static final String TOPIC_DOCUMENT_EVENTS = "loan.document.events";

    public void publishApplicationSubmitted(LoanApplication application) {
        LoanEvent event = LoanEvent.builder()
                .eventId(java.util.UUID.randomUUID().toString())
                .eventType("APPLICATION_SUBMITTED")
                .applicationId(application.getApplicationId())
                .timestamp(java.time.Instant.now())
                .payload(application)
                .build();

        kafkaTemplate.send(TOPIC_APPLICATION_EVENTS, application.getApplicationId(), event);
        log.info("Published APPLICATION_SUBMITTED event for: {}", application.getApplicationId());
    }

    public void publishApplicationStatusChanged(LoanApplication application) {
        LoanEvent event = LoanEvent.builder()
                .eventId(java.util.UUID.randomUUID().toString())
                .eventType("APPLICATION_STATUS_CHANGED")
                .applicationId(application.getApplicationId())
                .timestamp(java.time.Instant.now())
                .payload(application)
                .build();

        kafkaTemplate.send(TOPIC_APPLICATION_EVENTS, application.getApplicationId(), event);
        log.info("Published APPLICATION_STATUS_CHANGED event for: {}", application.getApplicationId());
    }

    public void publishUnderwritingDecision(LoanApplication application, com.loanorigination.model.UnderwritingDecision decision) {
        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("application", application);
        payload.put("decision", decision);

        LoanEvent event = LoanEvent.builder()
                .eventId(java.util.UUID.randomUUID().toString())
                .eventType("UNDERWRITING_DECISION_MADE")
                .applicationId(application.getApplicationId())
                .timestamp(java.time.Instant.now())
                .payload(payload)
                .build();

        kafkaTemplate.send("loan.underwriting.decisions", application.getApplicationId(), event);
        log.info("Published UNDERWRITING_DECISION_MADE event for: {} - Decision: {}", 
            application.getApplicationId(), decision.getDecision());
    }

    public void publishDocumentUploaded(LoanDocument document) {
        LoanEvent event = LoanEvent.builder()
                .eventId(java.util.UUID.randomUUID().toString())
                .eventType("DOCUMENT_UPLOADED")
                .applicationId(String.valueOf(document.getApplicationId()))
                .timestamp(java.time.Instant.now())
                .payload(document)
                .build();

        kafkaTemplate.send(TOPIC_DOCUMENT_EVENTS,
                String.valueOf(document.getApplicationId()), event);
        log.info("Published DOCUMENT_UPLOADED event for applicationId={}, documentId={}",
                document.getApplicationId(), document.getId());
    }

    @lombok.Data
    @lombok.Builder
    public static class LoanEvent {
        private String eventId;
        private String eventType;
        private String applicationId;
        private java.time.Instant timestamp;
        private Object payload;
    }
}
