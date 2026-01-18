package com.healthcare.claims.infrastructure.elasticsearch;

import com.healthcare.claims.domain.model.Claim;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * Service for indexing claims to Elasticsearch.
 */
@ApplicationScoped
public class ClaimIndexer {

    private static final Logger LOG = Logger.getLogger(ClaimIndexer.class);
    private static final String CLAIMS_INDEX = "claims";

    @Inject
    ElasticsearchClient esClient;

    /**
     * Indexes a claim document.
     */
    public Uni<Void> indexClaim(Claim claim) {
        return Uni.createFrom().item(() -> {
            try {
                Map<String, Object> document = buildDocument(claim);

                esClient.index(i -> i
                    .index(CLAIMS_INDEX)
                    .id(claim.getId().toString())
                    .document(document)
                );

                LOG.infof("Indexed claim: %s", claim.getClaimNumber());

            } catch (Exception e) {
                LOG.errorf(e, "Failed to index claim: %s", claim.getClaimNumber());
            }
            return null;
        });
    }

    /**
     * Deletes a claim from the index.
     */
    public Uni<Void> deleteClaim(String claimId) {
        return Uni.createFrom().item(() -> {
            try {
                esClient.delete(d -> d
                    .index(CLAIMS_INDEX)
                    .id(claimId)
                );
                LOG.infof("Deleted claim from index: %s", claimId);

            } catch (Exception e) {
                LOG.errorf(e, "Failed to delete claim from index: %s", claimId);
            }
            return null;
        });
    }

    /**
     * Builds the Elasticsearch document from a Claim entity.
     */
    private Map<String, Object> buildDocument(Claim claim) {
        Map<String, Object> doc = new HashMap<>();

        doc.put("id", claim.getId().toString());
        doc.put("claimNumber", claim.getClaimNumber());
        doc.put("type", claim.getType().name());
        doc.put("status", claim.getStatus().name());
        doc.put("amount", claim.getAmount());
        doc.put("allowedAmount", claim.getAllowedAmount());
        doc.put("serviceDate", claim.getServiceDate().toString());
        doc.put("patientId", claim.getPatientId().toString());
        doc.put("providerId", claim.getProviderId().toString());
        doc.put("diagnosisCodes", claim.getDiagnosisCodes());
        doc.put("procedureCodes", claim.getProcedureCodes());
        doc.put("fraudScore", claim.getFraudScore());
        doc.put("fraudReasons", claim.getFraudReasons());
        doc.put("denialReason", claim.getDenialReason());
        doc.put("notes", claim.getNotes());
        doc.put("createdAt", claim.getCreatedAt() != null ? claim.getCreatedAt().toString() : null);
        doc.put("updatedAt", claim.getUpdatedAt() != null ? claim.getUpdatedAt().toString() : null);

        return doc;
    }
}
