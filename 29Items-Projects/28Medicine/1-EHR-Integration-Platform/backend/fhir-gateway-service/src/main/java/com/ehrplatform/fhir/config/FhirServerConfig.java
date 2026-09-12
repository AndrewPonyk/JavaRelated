package com.ehrplatform.fhir.config;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.RestfulServer;
import ca.uhn.fhir.rest.server.interceptor.ResponseHighlighterInterceptor;
import java.util.List;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the HAPI FHIR {@link RestfulServer} into Spring as a servlet mounted at
 * {@code /fhir/*}, registers all resource providers, and exposes a shared
 * {@link FhirContext} (expensive to build — create exactly one, app-wide).
 */
@Configuration
public class FhirServerConfig {

    /** A single, shared R4 context. Thread-safe and costly to instantiate. */
    @Bean
    public FhirContext fhirContext() {
        return FhirContext.forR4();
    }

    @Bean
    public ServletRegistrationBean<RestfulServer> fhirServletRegistration(
            FhirContext fhirContext, List<IResourceProvider> resourceProviders) {

        RestfulServer server = new EhrRestfulServer(fhirContext, resourceProviders);
        ServletRegistrationBean<RestfulServer> registration =
                new ServletRegistrationBean<>(server, "/fhir/*");
        registration.setName("FhirServlet");
        registration.setLoadOnStartup(1);
        return registration;
    }

    /**
     * {@link RestfulServer} that auto-registers every {@link IResourceProvider}
     * Spring bean (Patient, Observation, Encounter, …) plus a response
     * highlighter for human-friendly browser output. AuthZ + PHI audit are
     * enforced in the security filter chain and service layer respectively.
     */
    static class EhrRestfulServer extends RestfulServer {

        EhrRestfulServer(FhirContext ctx, List<IResourceProvider> providers) {
            super(ctx);
            registerProviders(providers);
            registerInterceptor(new ResponseHighlighterInterceptor());
        }
    }
}
