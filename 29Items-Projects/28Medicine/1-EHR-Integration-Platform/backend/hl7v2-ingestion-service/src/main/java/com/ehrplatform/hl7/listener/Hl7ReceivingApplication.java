package com.ehrplatform.hl7.listener;

import ca.uhn.hl7v2.AcknowledgmentCode;
import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.protocol.ReceivingApplication;
import ca.uhn.hl7v2.protocol.ReceivingApplicationException;
import com.ehrplatform.common.exception.ValidationException;
import com.ehrplatform.hl7.client.GatewayClient;
import com.ehrplatform.hl7.mapper.Hl7ToFhirMapper;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.stereotype.Component;

/**
 * Handles each inbound HL7v2 message: map to FHIR, submit to the gateway, and
 * return the appropriate ACK.
 *
 * <ul>
 *   <li>success → {@code AA} (application accept)</li>
 *   <li>mapping/validation error → {@code AE} (application error) — sender should not resend</li>
 *   <li>downstream failure → {@code AR} (application reject) — sender should resend later</li>
 * </ul>
 */
@Component
public class Hl7ReceivingApplication implements ReceivingApplication<Message> {

    private static final Logger log = LoggerFactory.getLogger(Hl7ReceivingApplication.class);

    private final Hl7ToFhirMapper mapper;
    private final GatewayClient gatewayClient;

    public Hl7ReceivingApplication(Hl7ToFhirMapper mapper, GatewayClient gatewayClient) {
        this.mapper = mapper;
        this.gatewayClient = gatewayClient;
    }

    @Override
    public boolean canProcess(Message message) {
        return true;
    }

    @Override
    public Message processMessage(Message message, Map<String, Object> metadata)
            throws ReceivingApplicationException, HL7Exception {
        try {
            Bundle bundle = mapper.toFhirBundle(message);
            List<String> created = gatewayClient.submit(bundle);
            log.info("Ingested HL7 {} -> {} FHIR resource(s)", message.getName(), created.size());
            return ack(message);
        } catch (ValidationException e) {
            // Bad message content — reject permanently (AE), don't ask for resend.
            log.warn("Rejecting HL7 message {}: {}", message.getName(), e.getMessage());
            return nack(message, AcknowledgmentCode.AE, e.getMessage());
        } catch (Exception e) {
            // Transient downstream failure — application reject (AR) so sender retries.
            log.error("Failed to ingest HL7 message {}", message.getName(), e);
            return nack(message, AcknowledgmentCode.AR, e.getMessage());
        }
    }

    private Message ack(Message message) throws HL7Exception {
        try {
            return message.generateACK();
        } catch (java.io.IOException e) {
            throw new HL7Exception("Failed to generate ACK: " + e.getMessage());
        }
    }

    private Message nack(Message message, AcknowledgmentCode code, String detail) throws HL7Exception {
        try {
            return message.generateACK(code, new HL7Exception(detail));
        } catch (java.io.IOException e) {
            throw new HL7Exception("Failed to generate NACK: " + e.getMessage());
        }
    }
}
