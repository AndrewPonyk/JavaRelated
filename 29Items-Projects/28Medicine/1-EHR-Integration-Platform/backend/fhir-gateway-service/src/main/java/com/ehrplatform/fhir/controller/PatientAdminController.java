package com.ehrplatform.fhir.controller;

import ca.uhn.fhir.rest.api.MethodOutcome;
import com.ehrplatform.fhir.service.PatientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Date;
import java.util.List;
import java.time.ZoneOffset;
import org.hl7.fhir.r4.model.Enumerations.AdministrativeGender;
import org.hl7.fhir.r4.model.Patient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Non-FHIR administrative/ops API — a plain JSON CRUD surface used by internal
 * tooling and smoke tests. Demonstrates the standard Spring MVC pattern:
 * {@code @Valid} request DTO → service layer → typed HTTP response.
 *
 * <p>Clinical integrations should prefer the FHIR endpoint at {@code /fhir/Patient}.
 */
@RestController
@RequestMapping("/api/v1/patients")
@Tag(name = "Patient Admin", description = "Administrative CRUD for patients (non-FHIR)")
public class PatientAdminController {

    private final PatientService patientService;

    public PatientAdminController(PatientService patientService) {
        this.patientService = patientService;
    }

    @PostMapping
    @Operation(summary = "Register a new patient")
    public ResponseEntity<PatientSummary> register(@Valid @RequestBody PatientRegistrationRequest request) {
        MethodOutcome outcome = patientService.create(toFhir(request, null));
        String id = outcome.getId().getIdPart();
        Patient created = patientService.read(id);
        return ResponseEntity.created(URI.create("/api/v1/patients/" + id)).body(PatientSummary.from(created));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a patient by id")
    public ResponseEntity<PatientSummary> get(@PathVariable String id) {
        return ResponseEntity.ok(PatientSummary.from(patientService.read(id)));
    }

    @GetMapping
    @Operation(summary = "Search patients by identifier or family name")
    public List<PatientSummary> search(
            @RequestParam(required = false) String identifier,
            @RequestParam(required = false) String family) {
        List<Patient> results;
        if (identifier != null && !identifier.isBlank()) {
            // identifier may be "system|value" or just "value"
            String[] parts = identifier.split("\\|", 2);
            results = parts.length == 2
                    ? patientService.searchByIdentifier(parts[0], parts[1])
                    : patientService.searchByIdentifier(null, parts[0]);
        } else if (family != null && !family.isBlank()) {
            results = patientService.searchByFamily(family);
        } else {
            results = List.of();
        }
        return results.stream().map(PatientSummary::from).toList();
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an existing patient")
    public ResponseEntity<PatientSummary> update(
            @PathVariable String id, @Valid @RequestBody PatientRegistrationRequest request) {
        patientService.update(id, toFhir(request, id));
        return ResponseEntity.ok(PatientSummary.from(patientService.read(id)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a patient")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        patientService.delete(id);
        return ResponseEntity.noContent().build();
    }

    private Patient toFhir(PatientRegistrationRequest r, String id) {
        Patient p = new Patient();
        if (id != null) {
            p.setId(id);
        }
        p.addIdentifier().setSystem(r.identifierSystem()).setValue(r.identifierValue());
        p.addName().setFamily(r.familyName()).addGiven(r.givenName());
        if (r.birthDate() != null) {
            p.setBirthDate(Date.from(r.birthDate().atStartOfDay(ZoneOffset.UTC).toInstant()));
        }
        if (r.gender() != null) {
            p.setGender(AdministrativeGender.fromCode(r.gender()));
        }
        return p;
    }
}
