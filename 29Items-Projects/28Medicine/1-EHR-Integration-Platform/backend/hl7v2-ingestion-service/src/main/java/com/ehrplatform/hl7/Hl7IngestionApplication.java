package com.ehrplatform.hl7;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * HL7v2 Ingestion Service.
 *
 * <p>Listens for HL7v2 messages over MLLP, parses them with HAPI's
 * {@code PipeParser}, maps supported message types (ADT/ORU/ORM) to FHIR R4,
 * and forwards them to the FHIR gateway. Returns ACK/NACK to the sender.
 */
@SpringBootApplication
public class Hl7IngestionApplication {

    public static void main(String[] args) {
        SpringApplication.run(Hl7IngestionApplication.class, args);
    }
}
