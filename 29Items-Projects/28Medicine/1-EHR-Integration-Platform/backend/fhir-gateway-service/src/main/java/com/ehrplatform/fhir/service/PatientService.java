package com.ehrplatform.fhir.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.MethodOutcome;
import com.ehrplatform.common.exception.ResourceNotFoundException;
import com.ehrplatform.common.exception.ValidationException;
import com.ehrplatform.fhir.domain.PatientIndex;
import com.ehrplatform.fhir.repository.PatientIndexRepository;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Patient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business logic for FHIR {@code Patient}: validation, persistence to the Oracle
 * index, PHI audit, and (transactionally) enqueuing lifecycle events to the
 * outbox. Single funnel for both the FHIR provider and the REST admin controller.
 */
@Service
public class PatientService {

    private static final Logger log = LoggerFactory.getLogger(PatientService.class);
    private static final String TYPE = "Patient";

    private final PatientIndexRepository repository;
    private final OutboxPublisher outboxPublisher;
    private final AuditService auditService;
    private final FhirContext fhirContext;

    public PatientService(PatientIndexRepository repository,
                          OutboxPublisher outboxPublisher,
                          AuditService auditService,
                          FhirContext fhirContext) {
        this.repository = repository;
        this.outboxPublisher = outboxPublisher;
        this.auditService = auditService;
        this.fhirContext = fhirContext;
    }

    @Transactional(readOnly = true)
    public Patient read(String id) {
        PatientIndex entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(TYPE, id));
        auditService.record("READ", TYPE, id);
        return toFhir(entity);
    }

    @Transactional(readOnly = true)
    public List<Patient> searchByIdentifier(String system, String value) {
        if (value == null || value.isBlank()) {
            throw new ValidationException("identifier value is required");
        }
        List<PatientIndex> results = (system == null || system.isBlank())
                ? repository.findByIdentifierValue(value)
                : repository.findByIdentifierSystemAndIdentifierValue(system, value);
        auditService.record("SEARCH", TYPE, "identifier=" + value);
        return results.stream().map(this::toFhir).toList();
    }

    @Transactional(readOnly = true)
    public List<Patient> searchByFamily(String family) {
        auditService.record("SEARCH", TYPE, "family=" + family);
        return repository.searchByFamily(family).stream().map(this::toFhir).toList();
    }

    @Transactional
    public MethodOutcome create(Patient patient) {
        validate(patient);
        String id = UUID.randomUUID().toString();
        PatientIndex entity = new PatientIndex(id);
        persist(patient, entity);
        outboxPublisher.enqueue("ehr.patient.created", TYPE, id, entity.getResourceJson());
        auditService.record("CREATE", TYPE, id);
        log.info("Created Patient id={}", id);
        return outcome(id, true);
    }

    @Transactional
    public MethodOutcome update(String id, Patient patient) {
        validate(patient);
        PatientIndex entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(TYPE, id));
        patient.setId(id);
        persist(patient, entity);
        outboxPublisher.enqueue("ehr.patient.updated", TYPE, id, entity.getResourceJson());
        auditService.record("UPDATE", TYPE, id);
        log.info("Updated Patient id={}", id);
        return outcome(id, false);
    }

    @Transactional
    public void delete(String id) {
        PatientIndex entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(TYPE, id));
        repository.delete(entity);
        outboxPublisher.enqueue("ehr.patient.deleted", TYPE, id, "{}");
        auditService.record("DELETE", TYPE, id);
        log.info("Deleted Patient id={}", id);
    }

    // --- helpers -------------------------------------------------------------

    private void validate(Patient patient) {
        if (patient == null || !patient.hasName() || patient.getNameFirstRep().getFamily() == null
                || patient.getNameFirstRep().getFamily().isBlank()) {
            throw new ValidationException("Patient.name.family is required");
        }
    }

    private void persist(Patient patient, PatientIndex entity) {
        entity.setResourceJson(fhirContext.newJsonParser()
                .encodeResourceToString(patient.setId(entity.getFhirId())));
        applyProjection(patient, entity);
        entity.setLastUpdated(Instant.now());
        repository.save(entity);
    }

    private void applyProjection(Patient patient, PatientIndex entity) {
        if (patient.hasIdentifier()) {
            entity.setIdentifierSystem(patient.getIdentifierFirstRep().getSystem());
            entity.setIdentifierValue(patient.getIdentifierFirstRep().getValue());
        }
        HumanName name = patient.getNameFirstRep();
        entity.setFamilyName(name.getFamily());
        entity.setGivenName(name.getGivenAsSingleString());
        if (patient.hasBirthDate()) {
            entity.setBirthDate(patient.getBirthDate().toInstant()
                    .atZone(ZoneOffset.UTC).toLocalDate());
        }
    }

    private Patient toFhir(PatientIndex entity) {
        if (entity.getResourceJson() != null) {
            Patient p = fhirContext.newJsonParser()
                    .parseResource(Patient.class, entity.getResourceJson());
            p.setId(entity.getFhirId());
            return p;
        }
        Patient p = new Patient();
        p.setId(entity.getFhirId());
        p.addName().setFamily(entity.getFamilyName()).addGiven(entity.getGivenName());
        return p;
    }

    private MethodOutcome outcome(String id, boolean created) {
        MethodOutcome outcome = new MethodOutcome();
        outcome.setId(new IdType(TYPE, id));
        outcome.setCreated(created);
        return outcome;
    }
}
