package com.ehrplatform.hl7.client;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Submits a mapped FHIR transaction bundle to the gateway.
 *
 * <p>The gateway exposes per-resource endpoints rather than a system-level
 * transaction operation, so this client creates each entry individually and
 * rewrites intra-bundle {@code urn:uuid:} references (e.g. an Observation's
 * subject) to the real {@code Patient/{id}} the gateway assigns. Patients are
 * mapped first, so dependents resolve.
 */
@Component
public class GatewayClient {

    private static final Logger log = LoggerFactory.getLogger(GatewayClient.class);

    private final IGenericClient client;

    public GatewayClient(FhirContext fhirContext,
                         @Value("${ehr.gateway.base-url:http://localhost:8081/fhir}") String baseUrl) {
        this.client = fhirContext.newRestfulGenericClient(baseUrl);
    }

    /** @return the created resource references (e.g. {@code Patient/123}). */
    public List<String> submit(Bundle bundle) {
        Map<String, String> urnToId = new HashMap<>();
        List<String> created = new ArrayList<>();

        for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
            Resource resource = entry.getResource();
            rewriteReferences(resource, urnToId);
            resource.setId((String) null); // let the server assign the id

            String realId = resource.fhirType() + "/"
                    + client.create().resource(resource).execute().getId().getIdPart();
            urnToId.put(entry.getFullUrl(), realId);
            created.add(realId);
        }
        log.info("Submitted {} resource(s) to gateway: {}", created.size(), created);
        return created;
    }

    private void rewriteReferences(Resource resource, Map<String, String> urnToId) {
        if (resource instanceof Observation o && o.hasSubject()) {
            remap(o.getSubject(), urnToId);
        } else if (resource instanceof Encounter e && e.hasSubject()) {
            remap(e.getSubject(), urnToId);
        }
    }

    private void remap(Reference reference, Map<String, String> urnToId) {
        String resolved = urnToId.get(reference.getReference());
        if (resolved != null) {
            reference.setReference(resolved);
        }
    }
}
