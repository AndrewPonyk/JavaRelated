package com.healthcare.claims.infrastructure.elasticsearch;

import com.healthcare.claims.api.dto.ClaimResponseDTO;
import com.healthcare.claims.domain.model.ClaimStatus;
import com.healthcare.claims.domain.model.ClaimType;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for searching claims using Elasticsearch.
 */
@ApplicationScoped
public class ClaimSearchService {

    private static final Logger LOG = Logger.getLogger(ClaimSearchService.class);
    private static final String CLAIMS_INDEX = "claims";

    @Inject
    ElasticsearchClient esClient;

    /**
     * Searches claims by query string.
     */
    public Uni<List<ClaimResponseDTO>> searchClaims(String query, int limit) {
        return Uni.createFrom().item(() -> {
            try {
                SearchResponse<Map> response = esClient.search(s -> s
                    .index(CLAIMS_INDEX)
                    .size(limit)
                    .query(q -> q
                        .bool(b -> b
                            .should(sh -> sh.match(m -> m.field("claimNumber").query(query).boost(2.0f)))
                            .should(sh -> sh.match(m -> m.field("diagnosisCodes").query(query)))
                            .should(sh -> sh.match(m -> m.field("procedureCodes").query(query)))
                            .should(sh -> sh.match(m -> m.field("notes").query(query)))
                            .minimumShouldMatch("1")
                        )
                    ),
                    Map.class
                );

                return mapSearchResults(response);

            } catch (Exception e) {
                LOG.errorf(e, "Failed to search claims: %s", query);
                return List.of();
            }
        });
    }

    /**
     * Searches claims by status.
     */
    public Uni<List<ClaimResponseDTO>> searchByStatus(String status, int limit) {
        return Uni.createFrom().item(() -> {
            try {
                SearchResponse<Map> response = esClient.search(s -> s
                    .index(CLAIMS_INDEX)
                    .size(limit)
                    .query(q -> q
                        .term(t -> t.field("status").value(status))
                    ),
                    Map.class
                );

                return mapSearchResults(response);

            } catch (Exception e) {
                LOG.errorf(e, "Failed to search claims by status: %s", status);
                return List.of();
            }
        });
    }

    /**
     * Searches claims flagged for fraud.
     */
    public Uni<List<ClaimResponseDTO>> searchFraudFlaggedClaims(double minScore, int limit) {
        return Uni.createFrom().item(() -> {
            try {
                SearchResponse<Map> response = esClient.search(s -> s
                    .index(CLAIMS_INDEX)
                    .size(limit)
                    .query(q -> q
                        .range(r -> r.field("fraudScore").gte(co.elastic.clients.json.JsonData.of(minScore)))
                    ),
                    Map.class
                );

                return mapSearchResults(response);

            } catch (Exception e) {
                LOG.errorf(e, "Failed to search fraud flagged claims");
                return List.of();
            }
        });
    }

    /**
     * Maps Elasticsearch search results to DTOs.
     */
    @SuppressWarnings("unchecked")
    private List<ClaimResponseDTO> mapSearchResults(SearchResponse<Map> response) {
        List<ClaimResponseDTO> results = new ArrayList<>();

        for (Hit<Map> hit : response.hits().hits()) {
            Map<String, Object> source = hit.source();
            if (source != null) {
                ClaimResponseDTO dto = mapToDTO(source);
                if (dto != null) {
                    results.add(dto);
                }
            }
        }

        return results;
    }

    /**
     * Maps Elasticsearch document to DTO.
     */
    private ClaimResponseDTO mapToDTO(Map<String, Object> source) {
        try {
            return ClaimResponseDTO.builder()
                .id(UUID.fromString((String) source.get("id")))
                .claimNumber((String) source.get("claimNumber"))
                .status(ClaimStatus.valueOf((String) source.get("status")))
                .type(ClaimType.valueOf((String) source.get("type")))
                .amount(new BigDecimal(source.get("amount").toString()))
                .fraudScore(source.get("fraudScore") != null ?
                    ((Number) source.get("fraudScore")).doubleValue() : null)
                .diagnosisCodes((String) source.get("diagnosisCodes"))
                .procedureCodes((String) source.get("procedureCodes"))
                .build();
        } catch (Exception e) {
            LOG.warnf("Failed to map search result: %s", e.getMessage());
            return null;
        }
    }
}
