package com.ehrplatform.fhir.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.MethodOutcome;
import com.ehrplatform.common.exception.ResourceNotFoundException;
import com.ehrplatform.common.exception.ValidationException;
import com.ehrplatform.fhir.domain.EncounterIndex;
import com.ehrplatform.fhir.repository.EncounterIndexRepository;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.IdType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Business logic for FHIR {@code Encounter} (full CRUD + patient search). */
@Service
public class EncounterService {

    private static final Logger log = LoggerFactory.getLogger(EncounterService.class);
    private static final String TYPE = "Encounter";

    private final EncounterIndexRepository repository;
    private final OutboxPublisher outboxPublisher;
    private final AuditService auditService;
    private final FhirContext fhirContext;

    public EncounterService(EncounterIndexRepository repository,
                            OutboxPublisher outboxPublisher,
                            AuditService auditService,
                            FhirContext fhirContext) {
        this.repository = repository;
        this.outboxPublisher = outboxPublisher;
        this.auditService = auditService;
        this.fhirContext = fhirContext;
    }

    @Transactional(readOnly = true)
    public Encounter read(String id) {
        EncounterIndex entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(TYPE, id));
        auditService.record("READ", TYPE, id);
        return toFhir(entity);
    }

    @Transactional(readOnly = true)
    public List<Encounter> searchByPatient(String patientId) {
        if (patientId == null || patientId.isBlank()) {
            throw new ValidationException("patient is required");
        }
        auditService.record("SEARCH", TYPE, "patient=" + patientId);
        return repository.findByPatientIdOrderByPeriodStartDesc(patientId)
                .stream().map(this::toFhir).toList();
    }

    @Transactional
    public MethodOutcome create(Encounter encounter) {
        validate(encounter);
        String id = UUID.randomUUID().toString();
        EncounterIndex entity = new EncounterIndex(id);
        persist(encounter, entity);
        outboxPublisher.enqueue("ehr.encounter.created", TYPE, id, entity.getResourceJson());
        auditService.record("CREATE", TYPE, id);
        log.info("Created Encounter id={}", id);
        return outcome(id, true);
    }

    @Transactional
    public MethodOutcome update(String id, Encounter encounter) {
        validate(encounter);
        EncounterIndex entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(TYPE, id));
        encounter.setId(id);
        persist(encounter, entity);
        outboxPublisher.enqueue("ehr.encounter.updated", TYPE, id, entity.getResourceJson());
        auditService.record("UPDATE", TYPE, id);
        return outcome(id, false);
    }

    @Transactional
    public void delete(String id) {
        EncounterIndex entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(TYPE, id));
        repository.delete(entity);
        outboxPublisher.enqueue("ehr.encounter.deleted", TYPE, id, "{}");
        auditService.record("DELETE", TYPE, id);
    }

    private void validate(Encounter enc) {
        if (enc == null || !enc.hasStatus()) {
            throw new ValidationException("Encounter.status is required");
        }
        if (!enc.hasSubject() || enc.getSubject().getReference() == null) {
            throw new ValidationException("Encounter.subject (patient) is required");
        }
    }

    private void persist(Encounter enc, EncounterIndex entity) {
        entity.setResourceJson(fhirContext.newJsonParser()
                .encodeResourceToString(enc.setId(entity.getFhirId())));
        entity.setPatientId(referenceId(enc.getSubject().getReference()));
        entity.setStatus(enc.getStatus().toCode());
        if (enc.hasClass_()) {
            entity.setClassCode(enc.getClass_().getCode());
        }
        if (enc.hasPeriod()) {
            entity.setPeriodStart(toInstant(enc.getPeriod().getStart()));
            entity.setPeriodEnd(toInstant(enc.getPeriod().getEnd()));
        }
        entity.setLastUpdated(Instant.now());
        repository.save(entity);
    }

    private Encounter toFhir(EncounterIndex entity) {
        Encounter e = fhirContext.newJsonParser()
                .parseResource(Encounter.class, entity.getResourceJson());
        e.setId(entity.getFhirId());
        return e;
    }

    private static Instant toInstant(Date date) {
        return date != null ? date.toInstant() : null;
    }

    private static String referenceId(String reference) {
        int slash = reference.lastIndexOf('/');
        return slash >= 0 ? reference.substring(slash + 1) : reference;
    }

    private MethodOutcome outcome(String id, boolean created) {
        MethodOutcome outcome = new MethodOutcome();
        outcome.setId(new IdType(TYPE, id));
        outcome.setCreated(created);
        return outcome;
    }
}
