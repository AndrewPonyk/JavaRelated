package com.healthcare.claims.api;

import com.healthcare.claims.api.dto.ProviderRequestDTO;
import com.healthcare.claims.api.dto.ProviderResponseDTO;
import com.healthcare.claims.domain.model.Provider;
import com.healthcare.claims.domain.service.ProviderService;
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
 * REST API resource for provider operations.
 */
@Path("/api/v1/providers")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Providers", description = "Healthcare provider management operations")
public class ProviderResource {

    private static final Logger LOG = Logger.getLogger(ProviderResource.class);
    private static final int MAX_PAGE_SIZE = 100;

    @Inject
    ProviderService providerService;

    @POST
    @Operation(summary = "Create a new provider", description = "Registers a new healthcare provider")
    @APIResponse(responseCode = "201", description = "Provider created successfully",
        content = @Content(schema = @Schema(implementation = ProviderResponseDTO.class)))
    @APIResponse(responseCode = "400", description = "Invalid provider data")
    @APIResponse(responseCode = "409", description = "Provider with NPI already exists")
    @RolesAllowed({"provider-admin", "admin"})
    public Uni<Response> createProvider(@Valid ProviderRequestDTO request) {
        LOG.infof("Creating provider with NPI: %s", request.getNpi());

        Provider provider = mapToEntity(request);

        return providerService.createProvider(provider)
            .map(savedProvider -> Response
                .status(Response.Status.CREATED)
                .entity(ProviderResponseDTO.fromEntity(savedProvider))
                .build())
            .onFailure().recoverWithItem(e -> {
                LOG.errorf(e, "Failed to create provider");
                if (e.getMessage() != null && e.getMessage().contains("already exists")) {
                    return Response
                        .status(Response.Status.CONFLICT)
                        .entity(new ErrorResponse(e.getMessage()))
                        .build();
                }
                return Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build();
            });
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Get provider by ID", description = "Retrieves a provider by unique identifier")
    @APIResponse(responseCode = "200", description = "Provider found",
        content = @Content(schema = @Schema(implementation = ProviderResponseDTO.class)))
    @APIResponse(responseCode = "404", description = "Provider not found")
    @RolesAllowed({"provider-viewer", "claims-viewer", "admin"})
    public Uni<Response> getProviderById(
            @Parameter(description = "Provider ID", required = true)
            @PathParam("id") UUID id) {

        return providerService.getProviderById(id)
            .map(provider -> {
                if (provider == null) {
                    return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ErrorResponse("Provider not found: " + id))
                        .build();
                }
                return Response.ok(ProviderResponseDTO.fromEntity(provider)).build();
            });
    }

    @GET
    @Path("/npi/{npi}")
    @Operation(summary = "Get provider by NPI")
    @RolesAllowed({"provider-viewer", "claims-viewer", "admin"})
    public Uni<Response> getProviderByNpi(
            @Parameter(description = "National Provider Identifier", required = true)
            @PathParam("npi") String npi) {

        return providerService.getProviderByNpi(npi)
            .map(provider -> {
                if (provider == null) {
                    return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ErrorResponse("Provider not found with NPI: " + npi))
                        .build();
                }
                return Response.ok(ProviderResponseDTO.fromEntity(provider)).build();
            });
    }

    @GET
    @Path("/tax-id/{taxId}")
    @Operation(summary = "Get provider by Tax ID")
    @RolesAllowed({"provider-viewer", "admin"})
    public Uni<Response> getProviderByTaxId(
            @Parameter(description = "Tax ID", required = true)
            @PathParam("taxId") String taxId) {

        return providerService.getProviderByTaxId(taxId)
            .map(provider -> {
                if (provider == null) {
                    return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ErrorResponse("Provider not found with Tax ID: " + taxId))
                        .build();
                }
                return Response.ok(ProviderResponseDTO.fromEntity(provider)).build();
            });
    }

    @GET
    @Operation(summary = "List providers", description = "Retrieves a paginated list of providers")
    @RolesAllowed({"provider-viewer", "claims-viewer", "admin"})
    public Uni<List<ProviderResponseDTO>> listProviders(
            @Parameter(description = "Page number (0-based)")
            @QueryParam("page") @DefaultValue("0") int page,
            @Parameter(description = "Page size (max 100)")
            @QueryParam("size") @DefaultValue("20") int size) {

        int validatedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return providerService.getAllProviders(page, validatedSize)
            .map(providers -> providers.stream()
                .map(ProviderResponseDTO::fromEntity)
                .toList());
    }

    @GET
    @Path("/active")
    @Operation(summary = "List active providers")
    @RolesAllowed({"provider-viewer", "claims-viewer", "admin"})
    public Uni<List<ProviderResponseDTO>> listActiveProviders() {
        return providerService.getActiveProviders()
            .map(providers -> providers.stream()
                .map(ProviderResponseDTO::fromEntity)
                .toList());
    }

    @GET
    @Path("/in-network")
    @Operation(summary = "List in-network providers")
    @RolesAllowed({"provider-viewer", "claims-viewer", "admin"})
    public Uni<List<ProviderResponseDTO>> listInNetworkProviders() {
        return providerService.getInNetworkProviders()
            .map(providers -> providers.stream()
                .map(ProviderResponseDTO::fromEntity)
                .toList());
    }

    @GET
    @Path("/specialty/{specialty}")
    @Operation(summary = "List providers by specialty")
    @RolesAllowed({"provider-viewer", "claims-viewer", "admin"})
    public Uni<List<ProviderResponseDTO>> listProvidersBySpecialty(
            @Parameter(description = "Medical specialty", required = true)
            @PathParam("specialty") String specialty) {

        return providerService.getProvidersBySpecialty(specialty)
            .map(providers -> providers.stream()
                .map(ProviderResponseDTO::fromEntity)
                .toList());
    }

    @GET
    @Path("/fraud-flagged")
    @Operation(summary = "List fraud-flagged providers")
    @RolesAllowed({"fraud-reviewer", "admin"})
    public Uni<List<ProviderResponseDTO>> listFraudFlaggedProviders() {
        return providerService.getFraudFlaggedProviders()
            .map(providers -> providers.stream()
                .map(ProviderResponseDTO::fromEntity)
                .toList());
    }

    @GET
    @Path("/search")
    @Operation(summary = "Search providers by name")
    @RolesAllowed({"provider-viewer", "claims-viewer", "admin"})
    public Uni<List<ProviderResponseDTO>> searchProviders(
            @Parameter(description = "Name to search for", required = true)
            @QueryParam("name") String name) {

        if (name == null || name.isBlank()) {
            return Uni.createFrom().item(List.of());
        }

        return providerService.searchProvidersByName(name)
            .map(providers -> providers.stream()
                .map(ProviderResponseDTO::fromEntity)
                .toList());
    }

    @PUT
    @Path("/{id}")
    @Operation(summary = "Update a provider", description = "Updates an existing provider")
    @APIResponse(responseCode = "200", description = "Provider updated successfully")
    @APIResponse(responseCode = "404", description = "Provider not found")
    @APIResponse(responseCode = "409", description = "NPI already in use")
    @RolesAllowed({"provider-admin", "admin"})
    public Uni<Response> updateProvider(
            @Parameter(description = "Provider ID", required = true)
            @PathParam("id") UUID id,
            @Valid ProviderRequestDTO request) {

        Provider provider = mapToEntity(request);

        return providerService.updateProvider(id, provider)
            .map(updatedProvider -> Response.ok(ProviderResponseDTO.fromEntity(updatedProvider)).build())
            .onFailure().recoverWithItem(e -> {
                LOG.errorf(e, "Failed to update provider");
                if (e.getMessage() != null && e.getMessage().contains("not found")) {
                    return Response
                        .status(Response.Status.NOT_FOUND)
                        .entity(new ErrorResponse(e.getMessage()))
                        .build();
                }
                if (e.getMessage() != null && e.getMessage().contains("already in use")) {
                    return Response
                        .status(Response.Status.CONFLICT)
                        .entity(new ErrorResponse(e.getMessage()))
                        .build();
                }
                return Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build();
            });
    }

    @PATCH
    @Path("/{id}/fraud-score")
    @Operation(summary = "Update provider fraud risk score")
    @RolesAllowed({"fraud-reviewer", "admin"})
    public Uni<Response> updateFraudScore(
            @PathParam("id") UUID id,
            @QueryParam("score") Double score) {

        if (score == null || score < 0 || score > 1) {
            return Uni.createFrom().item(
                Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("Score must be between 0 and 1"))
                    .build()
            );
        }

        return providerService.updateFraudRiskScore(id, score)
            .map(provider -> Response.ok(ProviderResponseDTO.fromEntity(provider)).build())
            .onFailure().recoverWithItem(e ->
                Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build()
            );
    }

    @PATCH
    @Path("/{id}/credentialing-status")
    @Operation(summary = "Update provider credentialing status")
    @RolesAllowed({"provider-admin", "admin"})
    public Uni<Response> updateCredentialingStatus(
            @PathParam("id") UUID id,
            @QueryParam("status") String status) {

        if (status == null || status.isBlank()) {
            return Uni.createFrom().item(
                Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("Status is required"))
                    .build()
            );
        }

        return providerService.updateCredentialingStatus(id, status)
            .map(provider -> Response.ok(ProviderResponseDTO.fromEntity(provider)).build())
            .onFailure().recoverWithItem(e ->
                Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build()
            );
    }

    @POST
    @Path("/{id}/deactivate")
    @Operation(summary = "Deactivate a provider")
    @RolesAllowed({"provider-admin", "admin"})
    public Uni<Response> deactivateProvider(@PathParam("id") UUID id) {
        return providerService.deactivateProvider(id)
            .map(provider -> Response.ok(ProviderResponseDTO.fromEntity(provider)).build())
            .onFailure().recoverWithItem(e ->
                Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build()
            );
    }

    @POST
    @Path("/{id}/reactivate")
    @Operation(summary = "Reactivate a provider")
    @RolesAllowed({"provider-admin", "admin"})
    public Uni<Response> reactivateProvider(@PathParam("id") UUID id) {
        return providerService.reactivateProvider(id)
            .map(provider -> Response.ok(ProviderResponseDTO.fromEntity(provider)).build())
            .onFailure().recoverWithItem(e ->
                Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build()
            );
    }

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Delete a provider", description = "Permanently deletes a provider")
    @APIResponse(responseCode = "204", description = "Provider deleted")
    @APIResponse(responseCode = "404", description = "Provider not found")
    @RolesAllowed({"admin"})
    public Uni<Response> deleteProvider(@PathParam("id") UUID id) {
        return providerService.deleteProvider(id)
            .map(deleted -> {
                if (deleted) {
                    return Response.noContent().build();
                }
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("Provider not found: " + id))
                    .build();
            });
    }

    @GET
    @Path("/count")
    @Operation(summary = "Get provider count")
    @RolesAllowed({"provider-viewer", "admin"})
    public Uni<Response> getProviderCount() {
        return Uni.combine().all().unis(
            providerService.countProviders(),
            providerService.countInNetworkProviders()
        ).asTuple()
        .map(tuple -> Response.ok(new CountResponse(tuple.getItem1(), tuple.getItem2())).build());
    }

    private Provider mapToEntity(ProviderRequestDTO dto) {
        return Provider.builder()
            .npi(dto.getNpi())
            .name(dto.getName())
            .specialty(dto.getSpecialty())
            .taxId(dto.getTaxId())
            .inNetwork(dto.getInNetwork())
            .providerType(dto.getProviderType())
            .email(dto.getEmail())
            .phone(dto.getPhone())
            .address(dto.getAddress())
            .city(dto.getCity())
            .state(dto.getState())
            .zipCode(dto.getZipCode())
            .isActive(dto.getIsActive())
            .credentialingStatus(dto.getCredentialingStatus())
            .build();
    }

    public record ErrorResponse(String message) {}
    public record CountResponse(Long total, Long inNetwork) {}
}
