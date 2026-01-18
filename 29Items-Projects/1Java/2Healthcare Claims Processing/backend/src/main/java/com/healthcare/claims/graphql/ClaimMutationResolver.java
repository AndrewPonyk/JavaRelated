package com.healthcare.claims.graphql;

import com.healthcare.claims.api.dto.ClaimRequestDTO;
import com.healthcare.claims.api.dto.ClaimResponseDTO;
import com.healthcare.claims.domain.model.Claim;
import com.healthcare.claims.domain.model.ClaimStatus;
import com.healthcare.claims.domain.model.ClaimType;
import com.healthcare.claims.domain.service.ClaimService;
import io.smallrye.graphql.api.Nullable;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.graphql.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * GraphQL mutation resolver for claim operations.
 */
@GraphQLApi
@ApplicationScoped
public class ClaimMutationResolver {

    @Inject
    ClaimService claimService;

    @Mutation("submitClaim")
    @Description("Submit a new healthcare claim")
    public Uni<ClaimResponseDTO> submitClaim(
            @Name("input") @NonNull ClaimInput input) {

        Claim claim = Claim.builder()
            .type(input.type())
            .amount(input.amount())
            .serviceDate(input.serviceDate())
            .serviceEndDate(input.serviceEndDate())
            .patientId(input.patientId())
            .providerId(input.providerId())
            .diagnosisCodes(input.diagnosisCodes())
            .procedureCodes(input.procedureCodes())
            .notes(input.notes())
            .build();

        return claimService.submitClaim(claim)
            .map(ClaimResponseDTO::fromEntity);
    }

    @Mutation("updateClaimStatus")
    @Description("Update the status of a claim")
    public Uni<ClaimResponseDTO> updateClaimStatus(
            @Name("id") @NonNull UUID id,
            @Name("status") @NonNull ClaimStatus status,
            @Name("notes") @Nullable String notes) {

        return claimService.updateClaimStatus(id, status, notes)
            .map(ClaimResponseDTO::fromEntity);
    }

    @Mutation("approveClaim")
    @Description("Approve a claim after review")
    public Uni<ClaimResponseDTO> approveClaim(
            @Name("id") @NonNull UUID id,
            @Name("reviewedBy") @NonNull String reviewedBy,
            @Name("notes") @Nullable String notes) {

        return claimService.approveClaim(id, reviewedBy, notes)
            .map(ClaimResponseDTO::fromEntity);
    }

    @Mutation("denyClaim")
    @Description("Deny a claim with reason")
    public Uni<ClaimResponseDTO> denyClaim(
            @Name("id") @NonNull UUID id,
            @Name("reason") @NonNull String reason,
            @Name("reviewedBy") @NonNull String reviewedBy) {

        return claimService.denyClaim(id, reason, reviewedBy)
            .map(ClaimResponseDTO::fromEntity);
    }

    @Mutation("processClaim")
    @Description("Process a claim through adjudication")
    public Uni<ClaimResponseDTO> processClaim(
            @Name("id") @NonNull UUID id) {

        return claimService.processClaim(id)
            .map(ClaimResponseDTO::fromEntity);
    }

    /**
     * Input type for claim submission.
     */
    @Input("ClaimInput")
    public record ClaimInput(
        @NonNull ClaimType type,
        @NonNull BigDecimal amount,
        @NonNull LocalDate serviceDate,
        @Nullable LocalDate serviceEndDate,
        @NonNull UUID patientId,
        @NonNull UUID providerId,
        @Nullable String diagnosisCodes,
        @Nullable String procedureCodes,
        @Nullable String notes
    ) {}
}
