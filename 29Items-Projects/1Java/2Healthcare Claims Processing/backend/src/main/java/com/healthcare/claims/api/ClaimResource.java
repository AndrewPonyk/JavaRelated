package com.healthcare.claims.api;

import com.healthcare.claims.api.dto.ClaimRequestDTO;
import com.healthcare.claims.api.dto.ClaimResponseDTO;
import com.healthcare.claims.domain.model.Claim;
import com.healthcare.claims.domain.model.ClaimStatus;
import com.healthcare.claims.domain.service.ClaimService;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.UUID;

/**
 * REST API resource for claim operations.
 */
@Path("/api/v1/claims")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Claims", description = "Healthcare claims processing operations")
public class ClaimResource {

    private static final Logger LOG = Logger.getLogger(ClaimResource.class);

    @Inject
    ClaimService claimService;

    @POST
    @Operation(summary = "Submit a new claim", description = "Submits a new healthcare claim for processing")
    @APIResponse(responseCode = "201", description = "Claim submitted successfully",
        content = @Content(schema = @Schema(implementation = ClaimResponseDTO.class)))
    @APIResponse(responseCode = "400", description = "Invalid claim data")
    @APIResponse(responseCode = "422", description = "Claim validation failed")
    @RolesAllowed({"claims-submitter", "admin"})
    public Uni<Response> submitClaim(@Valid ClaimRequestDTO request) {
        LOG.infof("Received claim submission request for patient: %s", request.getPatientId());

        Claim claim = mapToEntity(request);

        return claimService.submitClaim(claim)
            .map(savedClaim -> Response
                .status(Response.Status.CREATED)
                .entity(ClaimResponseDTO.fromEntity(savedClaim))
                .build())
            .onFailure().recoverWithItem(e -> {
                LOG.errorf(e, "Failed to submit claim");
                return Response
                    .status(422)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build();
            });
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Get claim by ID", description = "Retrieves a claim by its unique identifier")
    @APIResponse(responseCode = "200", description = "Claim found",
        content = @Content(schema = @Schema(implementation = ClaimResponseDTO.class)))
    @APIResponse(responseCode = "404", description = "Claim not found")
    @RolesAllowed({"claims-viewer", "claims-processor", "admin"})
    public Uni<Response> getClaimById(
            @Parameter(description = "Claim ID", required = true)
            @PathParam("id") UUID id) {

        return claimService.getClaimById(id)
            .map(claim -> {
                if (claim == null) {
                    return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ErrorResponse("Claim not found: " + id))
                        .build();
                }
                return Response.ok(ClaimResponseDTO.fromEntity(claim)).build();
            });
    }

    @GET
    @Path("/number/{claimNumber}")
    @Operation(summary = "Get claim by claim number")
    @RolesAllowed({"claims-viewer", "claims-processor", "admin"})
    public Uni<Response> getClaimByNumber(
            @Parameter(description = "Claim number", required = true)
            @PathParam("claimNumber") String claimNumber) {

        return claimService.getClaimByNumber(claimNumber)
            .map(claim -> {
                if (claim == null) {
                    return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ErrorResponse("Claim not found: " + claimNumber))
                        .build();
                }
                return Response.ok(ClaimResponseDTO.fromEntity(claim)).build();
            });
    }

    private static final int MAX_PAGE_SIZE = 100;

    @GET
    @Operation(summary = "List claims", description = "Retrieves a paginated list of claims with optional filters")
    @RolesAllowed({"claims-viewer", "claims-processor", "admin"})
    public Uni<List<ClaimResponseDTO>> listClaims(
            @Parameter(description = "Filter by status")
            @QueryParam("status") ClaimStatus status,
            @Parameter(description = "Page number (0-based)")
            @QueryParam("page") @DefaultValue("0") int page,
            @Parameter(description = "Page size (max 100)")
            @QueryParam("size") @DefaultValue("20") int size) {

        int validatedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return claimService.getClaimsPaginated(status, page, validatedSize)
            .map(claims -> claims.stream()
                .map(ClaimResponseDTO::fromEntity)
                .toList());
    }

    @GET
    @Path("/patient/{patientId}")
    @Operation(summary = "Get claims for patient")
    @RolesAllowed({"claims-viewer", "claims-processor", "admin"})
    public Uni<List<ClaimResponseDTO>> getClaimsForPatient(
            @Parameter(description = "Patient ID", required = true)
            @PathParam("patientId") UUID patientId) {

        return claimService.getClaimsForPatient(patientId)
            .map(claims -> claims.stream()
                .map(ClaimResponseDTO::fromEntity)
                .toList());
    }

    @GET
    @Path("/review-queue")
    @Operation(summary = "Get claims requiring review")
    @RolesAllowed({"claims-processor", "fraud-reviewer", "admin"})
    public Uni<List<ClaimResponseDTO>> getReviewQueue() {
        return claimService.getClaimsRequiringReview()
            .map(claims -> claims.stream()
                .map(ClaimResponseDTO::fromEntity)
                .toList());
    }

    @PATCH
    @Path("/{id}/status")
    @Operation(summary = "Update claim status")
    @RolesAllowed({"claims-processor", "admin"})
    public Uni<Response> updateClaimStatus(
            @PathParam("id") UUID id,
            @QueryParam("status") ClaimStatus newStatus,
            @QueryParam("notes") String notes) {

        return claimService.updateClaimStatus(id, newStatus, notes)
            .map(claim -> Response.ok(ClaimResponseDTO.fromEntity(claim)).build())
            .onFailure().recoverWithItem(e ->
                Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build()
            );
    }

    @POST
    @Path("/{id}/approve")
    @Operation(summary = "Approve a claim")
    @RolesAllowed({"claims-processor", "admin"})
    public Uni<Response> approveClaim(
            @PathParam("id") UUID id,
            @QueryParam("reviewedBy") String reviewedBy,
            @QueryParam("notes") String notes) {

        return claimService.approveClaim(id, reviewedBy, notes)
            .map(claim -> Response.ok(ClaimResponseDTO.fromEntity(claim)).build())
            .onFailure().recoverWithItem(e ->
                Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build()
            );
    }

    @POST
    @Path("/{id}/deny")
    @Operation(summary = "Deny a claim")
    @RolesAllowed({"claims-processor", "admin"})
    public Uni<Response> denyClaim(
            @PathParam("id") UUID id,
            @QueryParam("reason") String reason,
            @QueryParam("reviewedBy") String reviewedBy) {

        if (reason == null || reason.isBlank()) {
            return Uni.createFrom().item(
                Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("Denial reason is required"))
                    .build()
            );
        }

        return claimService.denyClaim(id, reason, reviewedBy)
            .map(claim -> Response.ok(ClaimResponseDTO.fromEntity(claim)).build())
            .onFailure().recoverWithItem(e ->
                Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build()
            );
    }

    @POST
    @Path("/{id}/process")
    @Operation(summary = "Process a claim through adjudication")
    @RolesAllowed({"claims-processor", "admin"})
    public Uni<Response> processClaim(@PathParam("id") UUID id) {
        return claimService.processClaim(id)
            .map(claim -> Response.ok(ClaimResponseDTO.fromEntity(claim)).build())
            .onFailure().recoverWithItem(e ->
                Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build()
            );
    }

    /**
     * Maps request DTO to domain entity.
     */
    private Claim mapToEntity(ClaimRequestDTO dto) {
        return Claim.builder()
            .type(dto.getType())
            .amount(dto.getAmount())
            .serviceDate(dto.getServiceDate())
            .serviceEndDate(dto.getServiceEndDate())
            .patientId(dto.getPatientId())
            .providerId(dto.getProviderId())
            .diagnosisCodes(dto.getDiagnosisCodes())
            .procedureCodes(dto.getProcedureCodes())
            .notes(dto.getNotes())
            .build();
    }

    /**
     * Simple error response wrapper.
     */
    public record ErrorResponse(String message) {}
}
