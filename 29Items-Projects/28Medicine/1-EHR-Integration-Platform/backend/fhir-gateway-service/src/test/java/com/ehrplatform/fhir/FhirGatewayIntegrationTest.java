package com.ehrplatform.fhir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import com.ehrplatform.fhir.controller.PatientRegistrationRequest;
import com.ehrplatform.fhir.controller.PatientSummary;
import com.ehrplatform.fhir.repository.OutboxEventRepository;
import com.ehrplatform.fhir.repository.PhiAuditRepository;
import java.time.LocalDate;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * Full end-to-end test: boots the app on a random port (H2, no Kafka) and drives
 * the real FHIR endpoints via a HAPI client and the REST admin API via
 * TestRestTemplate — covering providers, services, repositories, validation,
 * exception mapping, and the transactional outbox + PHI audit side effects.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class FhirGatewayIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired private TestRestTemplate rest;
    @Autowired private OutboxEventRepository outboxRepository;
    @Autowired private PhiAuditRepository auditRepository;

    private IGenericClient fhir;

    @BeforeEach
    void setUp() {
        FhirContext ctx = FhirContext.forR4();
        fhir = ctx.newRestfulGenericClient("http://localhost:" + port + "/fhir");
    }

    @Test
    void patient_fullLifecycleOverFhir() {
        // CREATE
        Patient p = new Patient();
        p.addIdentifier().setSystem("urn:mrn").setValue("MRN-1");
        p.addName().setFamily("Hopper").addGiven("Grace");
        String id = fhir.create().resource(p).execute().getId().getIdPart();
        assertThat(id).isNotBlank();

        // READ
        Patient read = fhir.read().resource(Patient.class).withId(id).execute();
        assertThat(read.getNameFirstRep().getFamily()).isEqualTo("Hopper");

        // SEARCH by family
        Bundle byFamily = fhir.search().forResource(Patient.class)
                .where(Patient.FAMILY.matches().value("Hopper"))
                .returnBundle(Bundle.class).execute();
        assertThat(byFamily.getEntry()).isNotEmpty();

        // SEARCH by identifier
        Bundle byId = fhir.search().forResource(Patient.class)
                .where(Patient.IDENTIFIER.exactly().systemAndIdentifier("urn:mrn", "MRN-1"))
                .returnBundle(Bundle.class).execute();
        assertThat(byId.getEntry()).hasSize(1);

        // UPDATE
        read.getNameFirstRep().setFamily("Hopper-Updated");
        fhir.update().resource(read).execute();
        assertThat(fhir.read().resource(Patient.class).withId(id).execute()
                .getNameFirstRep().getFamily()).isEqualTo("Hopper-Updated");

        // DELETE
        fhir.delete().resourceById(new IdType("Patient", id)).execute();
        assertThatThrownBy(() -> fhir.read().resource(Patient.class).withId(id).execute())
                .isInstanceOf(ResourceNotFoundException.class);

        // Side effects: outbox event(s) + audit trail were written.
        assertThat(outboxRepository.findAll())
                .anyMatch(e -> e.getEventType().equals("ehr.patient.created"));
        assertThat(auditRepository.findAll())
                .anyMatch(a -> a.getAction().equals("CREATE") && a.getResourceType().equals("Patient"));
    }

    @Test
    void observation_createAndSearchByPatient() {
        Patient p = new Patient();
        p.addName().setFamily("Lovelace").addGiven("Ada");
        String patientId = fhir.create().resource(p).execute().getId().getIdPart();

        Observation o = new Observation();
        o.setStatus(Observation.ObservationStatus.FINAL);
        o.getCode().addCoding().setSystem("http://loinc.org").setCode("8867-4").setDisplay("Heart rate");
        o.getSubject().setReference("Patient/" + patientId);
        fhir.create().resource(o).execute();

        Bundle obs = fhir.search().forResource(Observation.class)
                .where(Observation.PATIENT.hasId(patientId))
                .returnBundle(Bundle.class).execute();
        assertThat(obs.getEntry()).hasSize(1);
    }

    @Test
    void encounter_createAndSearchByPatient() {
        Patient p = new Patient();
        p.addName().setFamily("Curie").addGiven("Marie");
        String patientId = fhir.create().resource(p).execute().getId().getIdPart();

        Encounter e = new Encounter();
        e.setStatus(Encounter.EncounterStatus.FINISHED);
        e.getClass_().setCode("AMB").setSystem("http://terminology.hl7.org/CodeSystem/v3-ActCode");
        e.getSubject().setReference("Patient/" + patientId);
        fhir.create().resource(e).execute();

        Bundle encounters = fhir.search().forResource(Encounter.class)
                .where(Encounter.PATIENT.hasId(patientId))
                .returnBundle(Bundle.class).execute();
        assertThat(encounters.getEntry()).hasSize(1);
    }

    @Test
    void restApi_registerSearchAndValidation() {
        PatientRegistrationRequest valid = new PatientRegistrationRequest(
                "urn:mrn", "REST-1", "Turing", "Alan", LocalDate.of(1912, 6, 23), "male");
        ResponseEntity<PatientSummary> created =
                rest.postForEntity("/api/v1/patients", valid, PatientSummary.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).isNotNull();
        assertThat(created.getBody().familyName()).isEqualTo("Turing");

        // SEARCH via REST
        ResponseEntity<PatientSummary[]> found =
                rest.getForEntity("/api/v1/patients?family=Turing", PatientSummary[].class);
        assertThat(found.getBody()).isNotEmpty();

        // VALIDATION: missing required familyName -> 422
        PatientRegistrationRequest invalid = new PatientRegistrationRequest(
                "urn:mrn", "REST-2", "", "Alan", LocalDate.of(1912, 6, 23), "male");
        ResponseEntity<String> rejected =
                rest.postForEntity("/api/v1/patients", invalid, String.class);
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void restApi_notFoundReturns404() {
        ResponseEntity<String> resp =
                rest.getForEntity("/api/v1/patients/does-not-exist", String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
