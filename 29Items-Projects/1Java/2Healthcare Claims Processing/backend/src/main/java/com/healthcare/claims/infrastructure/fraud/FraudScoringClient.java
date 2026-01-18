package com.healthcare.claims.infrastructure.fraud;

import com.healthcare.claims.domain.model.Claim;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * Client for external ML-based fraud scoring API.
 */
@ApplicationScoped
public class FraudScoringClient {

    private static final Logger LOG = Logger.getLogger(FraudScoringClient.class);

    @ConfigProperty(name = "fraud.api.url", defaultValue = "http://localhost:8090")
    String fraudApiUrl;

    @ConfigProperty(name = "fraud.api.key", defaultValue = "")
    String apiKey;

    @ConfigProperty(name = "fraud.api.timeout", defaultValue = "5000")
    int timeout;

    private volatile Client client;

    private Client getClient() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    client = ClientBuilder.newClient();
                }
            }
        }
        return client;
    }

    /**
     * Gets fraud score from external ML API.
     */
    public Uni<Double> getScore(Claim claim) {
        return Uni.createFrom().item(() -> {
            try {
                Map<String, Object> request = buildScoringRequest(claim);

                Response response = getClient().target(fraudApiUrl)
                    .path("/api/v1/score")
                    .request(MediaType.APPLICATION_JSON)
                    .header("X-API-Key", apiKey)
                    .post(Entity.json(request));

                if (response.getStatus() == 200) {
                    Map<String, Object> result = response.readEntity(Map.class);
                    Double score = ((Number) result.get("score")).doubleValue();
                    LOG.infof("ML fraud score for claim %s: %.2f",
                        claim.getClaimNumber(), score);
                    return score;
                } else {
                    LOG.warnf("Fraud API returned status %d for claim %s",
                        response.getStatus(), claim.getClaimNumber());
                    return 0.0;
                }

            } catch (Exception e) {
                LOG.errorf(e, "Failed to get fraud score for claim: %s",
                    claim.getClaimNumber());
                // Return default score on failure - don't block claim processing
                return 0.0;
            }
        });
    }

    /**
     * Builds the request payload for fraud scoring API.
     */
    private Map<String, Object> buildScoringRequest(Claim claim) {
        Map<String, Object> request = new HashMap<>();

        // Claim attributes
        request.put("claimId", claim.getId().toString());
        request.put("claimType", claim.getType().name());
        request.put("amount", claim.getAmount().doubleValue());
        request.put("serviceDate", claim.getServiceDate().toString());

        // Patient attributes
        request.put("patientId", claim.getPatientId().toString());

        // Provider attributes
        request.put("providerId", claim.getProviderId().toString());

        // Clinical data
        request.put("diagnosisCodes", claim.getDiagnosisCodes());
        request.put("procedureCodes", claim.getProcedureCodes());

        // TODO: Add more features for ML model:
        // - Historical claim patterns
        // - Provider network status
        // - Geographic data
        // - Time of submission
        // - Claim velocity

        return request;
    }

    /**
     * Health check for fraud API.
     */
    public Uni<Boolean> healthCheck() {
        return Uni.createFrom().item(() -> {
            try {
                Response response = getClient().target(fraudApiUrl)
                    .path("/health")
                    .request()
                    .get();

                return response.getStatus() == 200;
            } catch (Exception e) {
                LOG.warnf("Fraud API health check failed: %s", e.getMessage());
                return false;
            }
        });
    }
}
