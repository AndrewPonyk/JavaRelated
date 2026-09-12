package com.ehrplatform.events.producer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ehrplatform.common.dto.EhrEventEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class EhrEventProducerTest {

    @Mock private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void publishSerializesEnvelopeAndKeysByResourceId() {
        when(kafkaTemplate.send(eq("topic"), eq("p1"), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new CompletableFuture<SendResult<String, String>>());

        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        EhrEventProducer producer = new EhrEventProducer(kafkaTemplate, mapper);

        EhrEventEnvelope event = new EhrEventEnvelope(
                "e1", "ehr.patient.created", "Patient", "p1",
                "src", Instant.now(), "t", "{}");
        producer.publish("topic", event);

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("topic"), eq("p1"), payload.capture());
        assertThat(payload.getValue()).contains("\"eventId\":\"e1\"").contains("ehr.patient.created");
    }
}
