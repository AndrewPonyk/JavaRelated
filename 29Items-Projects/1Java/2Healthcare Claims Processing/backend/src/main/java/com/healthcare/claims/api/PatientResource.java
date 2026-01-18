package com.healthcare.claims.api;

import com.healthcare.claims.api.dto.PatientRequestDTO;
import com.healthcare.claims.api.dto.PatientResponseDTO;
import com.healthcare.claims.domain.model.Patient;
import com.healthcare.claims.domain.service.PatientService;
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
 * REST API resource for patient operations.
 */
@Path("/api/v1/patients")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Patients", description = "Patient management operations")
public class PatientResource {

    private static final Logger LOG = Logger.getLogger(PatientResource.class);
    private static final int MAX_PAGE_SIZE = 100;

    @Inject
    PatientService patientService;

    @POST
    @Operation(summary = "Create a new patient", description = "Registers a new patient in the system")
    @APIResponse(responseCode = "201", description = "Patient created successfully",
        content = @Content(schema = @Schema(implementation = PatientResponseDTO.class)))
    @APIResponse(responseCode = "400", description = "Invalid patient data")
    @APIResponse(responseCode = "409", description = "Patient with member ID already exists")
    @RolesAllowed({"patient-admin", "admin"})
    public Uni<Response> createPatient(@Valid PatientRequestDTO request) {
        LOG.infof("Creating patient with member ID: %s", request.getMemberId());

        Patient patient = mapToEntity(request);

        return patientService.createPatient(patient)
            .map(savedPatient -> Response
                .status(Response.Status.CREATED)
                .entity(PatientResponseDTO.fromEntity(savedPatient))
                .build())
            .onFailure().recoverWithItem(e -> {
                LOG.errorf(e, "Failed to create patient");
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
    @Operation(summary = "Get patient by ID", description = "Retrieves a patient by unique identifier")
    @APIResponse(responseCode = "200", description = "Patient found",
        content = @Content(schema = @Schema(implementation = PatientResponseDTO.class)))
    @APIResponse(responseCode = "404", description = "Patient not found")
    @RolesAllowed({"patient-viewer", "claims-viewer", "admin"})
    public Uni<Response> getPatientById(
            @Parameter(description = "Patient ID", required = true)
            @PathParam("id") UUID id) {

        return patientService.getPatientById(id)
            .map(patient -> {
                if (patient == null) {
                    return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ErrorResponse("Patient not found: " + id))
                        .build();
                }
                return Response.ok(PatientResponseDTO.fromEntity(patient)).build();
            });
    }

    @GET
    @Path("/member/{memberId}")
    @Operation(summary = "Get patient by member ID")
    @RolesAllowed({"patient-viewer", "claims-viewer", "admin"})
    public Uni<Response> getPatientByMemberId(
            @Parameter(description = "Member ID", required = true)
            @PathParam("memberId") String memberId) {

        return patientService.getPatientByMemberId(memberId)
            .map(patient -> {
                if (patient == null) {
                    return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ErrorResponse("Patient not found with member ID: " + memberId))
                        .build();
                }
                return Response.ok(PatientResponseDTO.fromEntity(patient)).build();
            });
    }

    @GET
    @Path("/policy/{policyNumber}")
    @Operation(summary = "Get patient by policy number")
    @RolesAllowed({"patient-viewer", "claims-viewer", "admin"})
    public Uni<Response> getPatientByPolicyNumber(
            @Parameter(description = "Policy number", required = true)
            @PathParam("policyNumber") String policyNumber) {

        return patientService.getPatientByPolicyNumber(policyNumber)
            .map(patient -> {
                if (patient == null) {
                    return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ErrorResponse("Patient not found with policy number: " + policyNumber))
                        .build();
                }
                return Response.ok(PatientResponseDTO.fromEntity(patient)).build();
            });
    }

    @GET
    @Operation(summary = "List patients", description = "Retrieves a paginated list of patients")
    @RolesAllowed({"patient-viewer", "claims-viewer", "admin"})
    public Uni<List<PatientResponseDTO>> listPatients(
            @Parameter(description = "Page number (0-based)")
            @QueryParam("page") @DefaultValue("0") int page,
            @Parameter(description = "Page size (max 100)")
            @QueryParam("size") @DefaultValue("20") int size) {

        int validatedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return patientService.getAllPatients(page, validatedSize)
            .map(patients -> patients.stream()
                .map(PatientResponseDTO::fromEntity)
                .toList());
    }

    @GET
    @Path("/active")
    @Operation(summary = "List active patients")
    @RolesAllowed({"patient-viewer", "claims-viewer", "admin"})
    public Uni<List<PatientResponseDTO>> listActivePatients() {
        return patientService.getActivePatients()
            .map(patients -> patients.stream()
                .map(PatientResponseDTO::fromEntity)
                .toList());
    }

    @GET
    @Path("/search")
    @Operation(summary = "Search patients by name")
    @RolesAllowed({"patient-viewer", "claims-viewer", "admin"})
    public Uni<List<PatientResponseDTO>> searchPatients(
            @Parameter(description = "Name to search for", required = true)
            @QueryParam("name") String name) {

        if (name == null || name.isBlank()) {
            return Uni.createFrom().item(List.of());
        }

        return patientService.searchPatientsByName(name)
            .map(patients -> patients.stream()
                .map(PatientResponseDTO::fromEntity)
                .toList());
    }

    @PUT
    @Path("/{id}")
    @Operation(summary = "Update a patient", description = "Updates an existing patient")
    @APIResponse(responseCode = "200", description = "Patient updated successfully")
    @APIResponse(responseCode = "404", description = "Patient not found")
    @APIResponse(responseCode = "409", description = "Member ID already in use")
    @RolesAllowed({"patient-admin", "admin"})
    public Uni<Response> updatePatient(
            @Parameter(description = "Patient ID", required = true)
            @PathParam("id") UUID id,
            @Valid PatientRequestDTO request) {

        Patient patient = mapToEntity(request);

        return patientService.updatePatient(id, patient)
            .map(updatedPatient -> Response.ok(PatientResponseDTO.fromEntity(updatedPatient)).build())
            .onFailure().recoverWithItem(e -> {
                LOG.errorf(e, "Failed to update patient");
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

    @POST
    @Path("/{id}/deactivate")
    @Operation(summary = "Deactivate a patient")
    @RolesAllowed({"patient-admin", "admin"})
    public Uni<Response> deactivatePatient(@PathParam("id") UUID id) {
        return patientService.deactivatePatient(id)
            .map(patient -> Response.ok(PatientResponseDTO.fromEntity(patient)).build())
            .onFailure().recoverWithItem(e ->
                Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build()
            );
    }

    @POST
    @Path("/{id}/reactivate")
    @Operation(summary = "Reactivate a patient")
    @RolesAllowed({"patient-admin", "admin"})
    public Uni<Response> reactivatePatient(@PathParam("id") UUID id) {
        return patientService.reactivatePatient(id)
            .map(patient -> Response.ok(PatientResponseDTO.fromEntity(patient)).build())
            .onFailure().recoverWithItem(e ->
                Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build()
            );
    }

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Delete a patient", description = "Permanently deletes a patient")
    @APIResponse(responseCode = "204", description = "Patient deleted")
    @APIResponse(responseCode = "404", description = "Patient not found")
    @RolesAllowed({"admin"})
    public Uni<Response> deletePatient(@PathParam("id") UUID id) {
        return patientService.deletePatient(id)
            .map(deleted -> {
                if (deleted) {
                    return Response.noContent().build();
                }
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("Patient not found: " + id))
                    .build();
            });
    }

    @GET
    @Path("/count")
    @Operation(summary = "Get patient count")
    @RolesAllowed({"patient-viewer", "admin"})
    public Uni<Response> getPatientCount() {
        return Uni.combine().all().unis(
            patientService.countPatients(),
            patientService.countActivePatients()
        ).asTuple()
        .map(tuple -> Response.ok(new CountResponse(tuple.getItem1(), tuple.getItem2())).build());
    }

    private Patient mapToEntity(PatientRequestDTO dto) {
        return Patient.builder()
            .memberId(dto.getMemberId())
            .firstName(dto.getFirstName())
            .lastName(dto.getLastName())
            .dateOfBirth(dto.getDateOfBirth())
            .policyNumber(dto.getPolicyNumber())
            .groupNumber(dto.getGroupNumber())
            .policyStartDate(dto.getPolicyStartDate())
            .policyEndDate(dto.getPolicyEndDate())
            .email(dto.getEmail())
            .phone(dto.getPhone())
            .address(dto.getAddress())
            .city(dto.getCity())
            .state(dto.getState())
            .zipCode(dto.getZipCode())
            .isActive(dto.getIsActive())
            .build();
    }

    public record ErrorResponse(String message) {}
    public record CountResponse(Long total, Long active) {}
}
