package com.ehrplatform.fhir.provider;

import ca.uhn.fhir.rest.annotation.Create;
import ca.uhn.fhir.rest.annotation.Delete;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.RequiredParam;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.annotation.Update;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.param.ReferenceParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import com.ehrplatform.fhir.service.ObservationService;
import java.util.List;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Observation;
import org.springframework.stereotype.Component;

/** FHIR R4 resource provider for {@code Observation}. */
@Component
public class ObservationResourceProvider implements IResourceProvider {

    private final ObservationService observationService;

    public ObservationResourceProvider(ObservationService observationService) {
        this.observationService = observationService;
    }

    @Override
    public Class<Observation> getResourceType() {
        return Observation.class;
    }

    @Read
    public Observation read(@IdParam IdType id) {
        return ProviderSupport.translate(() -> observationService.read(id.getIdPart()));
    }

    /** {@code GET /fhir/Observation?patient=Patient/{id}} */
    @Search
    public List<Observation> searchByPatient(
            @RequiredParam(name = Observation.SP_PATIENT) ReferenceParam patient) {
        return ProviderSupport.translate(() -> observationService.searchByPatient(patient.getIdPart()));
    }

    @Create
    public MethodOutcome create(@ResourceParam Observation observation) {
        return ProviderSupport.translate(() -> observationService.create(observation));
    }

    @Update
    public MethodOutcome update(@IdParam IdType id, @ResourceParam Observation observation) {
        return ProviderSupport.translate(() -> observationService.update(id.getIdPart(), observation));
    }

    @Delete
    public void delete(@IdParam IdType id) {
        ProviderSupport.run(() -> observationService.delete(id.getIdPart()));
    }
}
