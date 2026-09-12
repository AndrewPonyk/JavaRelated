package com.ehrplatform.hl7.parser;

import ca.uhn.hl7v2.DefaultHapiContext;
import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.parser.PipeParser;
import ca.uhn.hl7v2.validation.impl.ValidationContextFactory;
import com.ehrplatform.common.exception.ValidationException;
import org.springframework.stereotype.Component;

/**
 * Thin wrapper around HAPI's {@link PipeParser}.
 *
 * <p>Real-world HL7v2 is messy and vendor-specific, so parsing runs with
 * validation disabled ("be liberal in parsing"); structural rules are enforced
 * later in the mapper ("strict in mapping"). The {@link HapiContext} is
 * thread-safe and expensive — create one and reuse it.
 */
@Component
public class Hl7v2Parser {

    private final HapiContext hapiContext;
    private final PipeParser pipeParser;

    public Hl7v2Parser() {
        this.hapiContext = new DefaultHapiContext();
        this.hapiContext.setValidationContext(ValidationContextFactory.noValidation());
        this.pipeParser = hapiContext.getPipeParser();
    }

    /**
     * Parse a raw HL7v2 pipe-delimited message into a typed HAPI {@link Message}.
     *
     * @throws ValidationException if the message is not well-formed HL7v2
     */
    public Message parse(String rawMessage) {
        try {
            return pipeParser.parse(rawMessage);
        } catch (HL7Exception e) {
            throw new ValidationException("Malformed HL7v2 message: " + e.getMessage());
        }
    }
}
