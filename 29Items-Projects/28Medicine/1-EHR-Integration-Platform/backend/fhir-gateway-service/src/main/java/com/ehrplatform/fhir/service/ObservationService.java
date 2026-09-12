package com.ehrplatform.fhir.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.MethodOutcome;
import com.ehrplatform.common.exception.ResourceNotFoundException;
import com.ehrplatform.common.exception.ValidationException;
import com.ehrplatform.fhir.domain.ObservationIndex;
import com.ehrplatform.fhir.repository.ObservationIndexRepository;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Observation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Business logic for FHIR {@code Observation} (full CRUD + patient/code search). */
@Service
public class ObservationService {

    private static final Logger log = LoggerFactory.getLogger(ObservationService.class);
    private static final String TYPE = "Observation";

    private final ObservationIndexRepository repository;
    private final OutboxPublisher outboxPublisher;
    private final AuditService auditService;
    private final FhirContext fhirContext;

    public ObservationService(ObservationIndexRepository repository,
                              OutboxPublisher outboxPublisher,
                              AuditService auditService,
                              FhirContext fhirContext) {
        this.repository = repository;
        this.outboxPublisher = outboxPublisher;
        this.auditService = auditService;
        this.fhirContext = fhirContext;
    }

    @Transactional(readOnly = true)
    public Observation read(String id) {
        ObservationIndex entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(TYPE, id));
        auditService.record("READ", TYPE, id);
        return toFhir(entity);
    }

    @Transactional(readOnly = true)
    public List<Observation> searchByPatient(String patientId) {
        if (patientId == null || patientId.isBlank()) {
            throw new ValidationException("patient is required");
        }
        auditService.record("SEARCH", TYPE, "patient=" + patientId);
        return repository.findByPatientIdOrderByEffectiveAtDesc(patientId)
                .stream().map(this::toFhir).toList();
    }

    @Transactional
    public MethodOutcome create(Observation observation) {
        validate(observation);
        String id = UUID.randomUUID().toString();
        ObservationIndex entity = new ObservationIndex(id);
        persist(observation, entity);
        outboxPublisher.enqueue("ehr.observation.created", TYPE, id, entity.getResourceJson());
        auditService.record("CREATE", TYPE, id);
        log.info("Created Observation id={}", id);
        return outcome(id, true);
    }

    @Transactional
    public MethodOutcome update(String id, Observation observation) {
        validate(observation);
        ObservationIndex entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(TYPE, id));
        observation.setId(id);
        persist(observation, entity);
        outboxPublisher.enqueue("ehr.observation.updated", TYPE, id, entity.getResourceJson());
        auditService.record("UPDATE", TYPE, id);
        return outcome(id, false);
    }

    @Transactional
    public void delete(String id) {
        ObservationIndex entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(TYPE, id));
        repository.delete(entity);
        outboxPublisher.enqueue("ehr.observation.deleted", TYPE, id, "{}");
        auditService.record("DELETE", TYPE, id);
    }

    private void validate(Observation obs) {
        if (obs == null || !obs.hasStatus()) {
            throw new ValidationException("Observation.status is required");
        }
        if (!obs.hasCode() || obs.getCode().getCodingFirstRep().getCode() == null) {
            throw new ValidationException("Observation.code is required");
        }
        if (!obs.hasSubject() || obs.getSubject().getReference() == null) {
            throw new ValidationException("Observation.subject (patient) is required");
        }
    }

    private void persist(Observation obs, ObservationIndex entity) {
        entity.setResourceJson(fhirContext.newJsonParser()
                .encodeResourceToString(obs.setId(entity.getFhirId())));
        entity.setPatientId(referenceId(obs.getSubject().getReference()));
        Coding coding = obs.getCode().getCodingFirstRep();
        entity.setCodeSystem(coding.getSystem());
        entity.setCodeValue(coding.getCode());
        entity.setStatus(obs.getStatus().toCode());
        if (obs.hasEffectiveDateTimeType()) {
            Date d = obs.getEffectiveDateTimeType().getValue();
            entity.setEffectiveAt(d != null ? d.toInstant() : null);
        }
        entity.setLastUpdated(Instant.now());
        repository.save(entity);
    }

    private Observation toFhir(ObservationIndex entity) {
        Observation o = fhirContext.newJsonParser()
                .parseResource(Observation.class, entity.getResourceJson());
        o.setId(entity.getFhirId());
        return o;
    }

    private static String referenceId(String reference) {
        // "Patient/123" -> "123"
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
