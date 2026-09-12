package com.ehrplatform.hl7.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.util.Terser;
import com.ehrplatform.hl7.client.GatewayClient;
import com.ehrplatform.hl7.mapper.Hl7ToFhirMapper;
import com.ehrplatform.hl7.parser.Hl7v2Parser;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class Hl7ReceivingApplicationTest {

    private final Hl7v2Parser parser = new Hl7v2Parser();
    private final Hl7ToFhirMapper mapper = new Hl7ToFhirMapper();

    @Mock private GatewayClient gatewayClient;

    private static final String ADT = String.join("\r",
            "MSH|^~\\&|A|B|C|D|20240101120000||ADT^A01|M1|P|2.5",
            "PID|1||MRN1^^^HOSP^MR||Doe^John||19800101|M",
            "PV1|1|I");

    private static final String ADT_NO_NAME = String.join("\r",
            "MSH|^~\\&|A|B|C|D|20240101120000||ADT^A01|M2|P|2.5",
            "PID|1||MRN1^^^HOSP^MR||");

    private Hl7ReceivingApplication app() {
        return new Hl7ReceivingApplication(mapper, gatewayClient);
    }

    @Test
    void returnsAaAckOnSuccess() throws Exception {
        when(gatewayClient.submit(any())).thenReturn(List.of("Patient/1", "Encounter/2"));

        Message ack = app().processMessage(parser.parse(ADT), new HashMap<>());

        assertThat(new Terser(ack).get("/MSA-1")).isEqualTo("AA");
    }

    @Test
    void returnsAeNackOnValidationError() throws Exception {
        Message ack = app().processMessage(parser.parse(ADT_NO_NAME), new HashMap<>());

        assertThat(new Terser(ack).get("/MSA-1")).isEqualTo("AE");
        verifyNoInteractions(gatewayClient);
    }

    @Test
    void returnsArNackOnDownstreamFailure() throws Exception {
        when(gatewayClient.submit(any())).thenThrow(new RuntimeException("gateway unavailable"));

        Message ack = app().processMessage(parser.parse(ADT), new HashMap<>());

        assertThat(new Terser(ack).get("/MSA-1")).isEqualTo("AR");
    }
}
