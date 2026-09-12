package com.ehrplatform.fhir.provider;

import ca.uhn.fhir.rest.annotation.Create;
import ca.uhn.fhir.rest.annotation.Delete;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.OptionalParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.annotation.Update;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.param.StringParam;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import com.ehrplatform.fhir.service.PatientService;
import java.util.List;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Patient;
import org.springframework.stereotype.Component;

/**
 * FHIR R4 resource provider for {@code Patient} — read, search, create, update,
 * delete. Thin protocol adapter over {@link PatientService}.
 */
@Component
public class PatientResourceProvider implements IResourceProvider {

    private final PatientService patientService;

    public PatientResourceProvider(PatientService patientService) {
        this.patientService = patientService;
    }

    @Override
    public Class<Patient> getResourceType() {
        return Patient.class;
    }

    /** {@code GET /fhir/Patient/{id}} */
    @Read
    public Patient read(@IdParam IdType id) {
        return ProviderSupport.translate(() -> patientService.read(id.getIdPart()));
    }

    /** {@code GET /fhir/Patient?identifier=...} or {@code ?family=...} */
    @Search
    public List<Patient> search(
            @OptionalParam(name = Patient.SP_IDENTIFIER) TokenParam identifier,
            @OptionalParam(name = Patient.SP_FAMILY) StringParam family) {
        if (identifier != null) {
            return ProviderSupport.translate(
                    () -> patientService.searchByIdentifier(identifier.getSystem(), identifier.getValue()));
        }
        if (family != null) {
            return ProviderSupport.translate(() -> patientService.searchByFamily(family.getValue()));
        }
        throw new InvalidRequestException("A search parameter is required: identifier or family");
    }

    /** {@code POST /fhir/Patient} */
    @Create
    public MethodOutcome create(@ResourceParam Patient patient) {
        return ProviderSupport.translate(() -> patientService.create(patient));
    }

    /** {@code PUT /fhir/Patient/{id}} */
    @Update
    public MethodOutcome update(@IdParam IdType id, @ResourceParam Patient patient) {
        return ProviderSupport.translate(() -> patientService.update(id.getIdPart(), patient));
    }

    /** {@code DELETE /fhir/Patient/{id}} */
    @Delete
    public void delete(@IdParam IdType id) {
        ProviderSupport.run(() -> patientService.delete(id.getIdPart()));
    }
}
