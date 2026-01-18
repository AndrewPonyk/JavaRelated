package com.healthcare.claims.graphql;

import com.healthcare.claims.api.dto.ClaimResponseDTO;
import com.healthcare.claims.domain.model.ClaimStatus;
import com.healthcare.claims.domain.service.ClaimService;
import com.healthcare.claims.infrastructure.elasticsearch.ClaimSearchService;
import io.smallrye.graphql.api.Nullable;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.graphql.*;

import java.util.List;
import java.util.UUID;

/**
 * GraphQL query resolver for claim operations.
 */
@GraphQLApi
@ApplicationScoped
public class ClaimQueryResolver {

    @Inject
    ClaimService claimService;

    @Inject
    ClaimSearchService searchService;

    @Query("claim")
    @Description("Get a claim by ID")
    public Uni<ClaimResponseDTO> getClaim(
            @Name("id") @NonNull UUID id) {
        return claimService.getClaimById(id)
            .map(ClaimResponseDTO::fromEntity);
    }

    @Query("claimByNumber")
    @Description("Get a claim by claim number")
    public Uni<ClaimResponseDTO> getClaimByNumber(
            @Name("claimNumber") @NonNull String claimNumber) {
        return claimService.getClaimByNumber(claimNumber)
            .map(ClaimResponseDTO::fromEntity);
    }

    @Query("claims")
    @Description("Get paginated list of claims with optional filters")
    public Uni<ClaimConnection> getClaims(
            @Name("filter") @Nullable ClaimFilter filter,
            @Name("pagination") @Nullable Pagination pagination) {

        ClaimStatus status = filter != null ? filter.status() : null;
        int page = pagination != null ? pagination.page() : 0;
        int size = pagination != null ? pagination.size() : 20;

        return claimService.getClaimsPaginated(status, page, size)
            .map(claims -> {
                List<ClaimResponseDTO> edges = claims.stream()
                    .map(ClaimResponseDTO::fromEntity)
                    .toList();
                return new ClaimConnection(edges, new PageInfo(page, size, edges.size() == size));
            });
    }

    @Query("claimsForPatient")
    @Description("Get all claims for a specific patient")
    public Uni<List<ClaimResponseDTO>> getClaimsForPatient(
            @Name("patientId") @NonNull UUID patientId) {
        return claimService.getClaimsForPatient(patientId)
            .map(claims -> claims.stream()
                .map(ClaimResponseDTO::fromEntity)
                .toList());
    }

    @Query("claimsRequiringReview")
    @Description("Get claims that require human review")
    public Uni<List<ClaimResponseDTO>> getClaimsRequiringReview() {
        return claimService.getClaimsRequiringReview()
            .map(claims -> claims.stream()
                .map(ClaimResponseDTO::fromEntity)
                .toList());
    }

    @Query("searchClaims")
    @Description("Search claims using Elasticsearch")
    public Uni<List<ClaimResponseDTO>> searchClaims(
            @Name("query") @NonNull String query,
            @Name("limit") @DefaultValue("20") int limit) {
        return searchService.searchClaims(query, limit);
    }

    /**
     * Input type for claim filtering.
     */
    public record ClaimFilter(
        @Nullable ClaimStatus status,
        @Nullable UUID patientId,
        @Nullable UUID providerId
    ) {}

    /**
     * Input type for pagination.
     */
    public record Pagination(
        @DefaultValue("0") int page,
        @DefaultValue("20") int size
    ) {}

    /**
     * Connection type for paginated claims.
     */
    public record ClaimConnection(
        List<ClaimResponseDTO> edges,
        PageInfo pageInfo
    ) {}

    /**
     * Page info for pagination.
     */
    public record PageInfo(
        int currentPage,
        int pageSize,
        boolean hasNextPage
    ) {}
}
