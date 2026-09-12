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
import com.ehrplatform.fhir.service.EncounterService;
import java.util.List;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.IdType;
import org.springframework.stereotype.Component;

/** FHIR R4 resource provider for {@code Encounter}. */
@Component
public class EncounterResourceProvider implements IResourceProvider {

    private final EncounterService encounterService;

    public EncounterResourceProvider(EncounterService encounterService) {
        this.encounterService = encounterService;
    }

    @Override
    public Class<Encounter> getResourceType() {
        return Encounter.class;
    }

    @Read
    public Encounter read(@IdParam IdType id) {
        return ProviderSupport.translate(() -> encounterService.read(id.getIdPart()));
    }

    /** {@code GET /fhir/Encounter?patient=Patient/{id}} */
    @Search
    public List<Encounter> searchByPatient(
            @RequiredParam(name = Encounter.SP_PATIENT) ReferenceParam patient) {
        return ProviderSupport.translate(() -> encounterService.searchByPatient(patient.getIdPart()));
    }

    @Create
    public MethodOutcome create(@ResourceParam Encounter encounter) {
        return ProviderSupport.translate(() -> encounterService.create(encounter));
    }

    @Update
    public MethodOutcome update(@IdParam IdType id, @ResourceParam Encounter encounter) {
        return ProviderSupport.translate(() -> encounterService.update(id.getIdPart(), encounter));
    }

    @Delete
    public void delete(@IdParam IdType id) {
        ProviderSupport.run(() -> encounterService.delete(id.getIdPart()));
    }
}
