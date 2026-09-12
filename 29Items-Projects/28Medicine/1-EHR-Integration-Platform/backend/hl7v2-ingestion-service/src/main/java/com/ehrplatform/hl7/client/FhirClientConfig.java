package com.ehrplatform.hl7.client;

import ca.uhn.fhir.context.FhirContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Provides the shared (expensive) R4 {@link FhirContext}. */
@Configuration
public class FhirClientConfig {

    @Bean
    public FhirContext fhirContext() {
        return FhirContext.forR4();
    }
}
