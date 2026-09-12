package com.ehrplatform.hl7.mapper;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.Primitive;
import ca.uhn.hl7v2.model.Type;
import ca.uhn.hl7v2.model.Varies;
import ca.uhn.hl7v2.model.v25.group.ORU_R01_OBSERVATION;
import ca.uhn.hl7v2.model.v25.group.ORU_R01_ORDER_OBSERVATION;
import ca.uhn.hl7v2.model.v25.group.ORU_R01_PATIENT_RESULT;
import ca.uhn.hl7v2.model.v25.message.ORU_R01;
import ca.uhn.hl7v2.model.v25.segment.OBX;
import ca.uhn.hl7v2.model.v25.segment.PID;
import ca.uhn.hl7v2.util.Terser;
import com.ehrplatform.common.exception.ValidationException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.UUID;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Enumerations.AdministrativeGender;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Maps parsed HL7v2 messages to FHIR R4 transaction bundles.
 *
 * <ul>
 *   <li>ADT^A01/A04/A08 → Patient + Encounter</li>
 *   <li>ORU^R01 → Patient + Observation(s)</li>
 * </ul>
 *
 * <p>Patient/Encounter fields are read with a {@link Terser} (resilient to
 * structural variation); ORU observations are walked via the typed model to
 * handle repeating OBX segments. Be liberal in parsing, strict in mapping.
 */
@Component
public class Hl7ToFhirMapper {

    private static final Logger log = LoggerFactory.getLogger(Hl7ToFhirMapper.class);

    public Bundle toFhirBundle(Message message) {
        Terser terser = new Terser(message);
        String messageType = get(terser, "/MSH-9-1");

        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.TRANSACTION);

        switch (messageType == null ? "" : messageType) {
            case "ADT" -> mapAdt(terser, bundle);
            case "ORU" -> mapOru((ORU_R01) message, terser, bundle);
            default -> throw new ValidationException("Unsupported HL7 message type: " + messageType);
        }
        return bundle;
    }

    // --- ADT -----------------------------------------------------------------

    private void mapAdt(Terser t, Bundle bundle) {
        Patient patient = patientFromPid(t);
        String patientUrn = addEntry(bundle, patient, "Patient");

        Encounter encounter = new Encounter();
        encounter.setStatus(encounterStatus(get(t, "/MSH-9-2")));
        String classCode = get(t, "/PV1-2");
        if (classCode != null) {
            encounter.getClass_().setCode(classCode).setSystem(
                    "http://terminology.hl7.org/CodeSystem/v3-ActCode");
        }
        encounter.setSubject(new Reference(patientUrn));
        addEntry(bundle, encounter, "Encounter");
    }

    // --- ORU -----------------------------------------------------------------

    private void mapOru(ORU_R01 oru, Terser t, Bundle bundle) {
        Patient patient = patientFromPid(t);
        String patientUrn = addEntry(bundle, patient, "Patient");

        ORU_R01_PATIENT_RESULT result = oru.getPATIENT_RESULT();
        for (int i = 0; i < result.getORDER_OBSERVATIONReps(); i++) {
            ORU_R01_ORDER_OBSERVATION order = result.getORDER_OBSERVATION(i);
            for (int j = 0; j < order.getOBSERVATIONReps(); j++) {
                ORU_R01_OBSERVATION obs = order.getOBSERVATION(j);
                Observation observation = observationFromObx(obs.getOBX(), patientUrn);
                if (observation != null) {
                    addEntry(bundle, observation, "Observation");
                }
            }
        }
    }

    private Observation observationFromObx(OBX obx, String patientUrn) {
        String code = value(obx.getObservationIdentifier().getIdentifier());
        if (code == null) {
            return null;
        }
        Observation observation = new Observation();
        observation.setStatus(Observation.ObservationStatus.FINAL);
        observation.getCode().addCoding()
                .setCode(code)
                .setSystem(systemFor(value(obx.getObservationIdentifier().getNameOfCodingSystem())))
                .setDisplay(value(obx.getObservationIdentifier().getText()));
        observation.setSubject(new Reference(patientUrn));

        String obxValue = primitiveValue(obx);
        if (obxValue != null) {
            observation.getValueStringType().setValue(obxValue);
        }
        return observation;
    }

    // --- shared PID mapping --------------------------------------------------

    private Patient patientFromPid(Terser t) {
        Patient patient = new Patient();
        String family = get(t, "/.PID-5-1");
        String given = get(t, "/.PID-5-2");
        if (family == null && given == null) {
            throw new ValidationException("HL7 PID-5 (patient name) is required");
        }
        patient.addName().setFamily(family).addGiven(given);

        String mrn = get(t, "/.PID-3-1");
        if (mrn != null) {
            String system = get(t, "/.PID-3-4");
            patient.addIdentifier()
                    .setSystem(system != null ? "urn:oid:" + system : "urn:mrn")
                    .setValue(mrn);
        }
        String dob = get(t, "/.PID-7-1");
        if (dob != null) {
            patient.setBirthDateElement(new org.hl7.fhir.r4.model.DateType(parseHl7Date(dob)));
        }
        patient.setGender(gender(get(t, "/.PID-8")));
        return patient;
    }

    // --- helpers -------------------------------------------------------------

    private static String get(Terser t, String path) {
        try {
            String v = t.get(path);
            return (v == null || v.isBlank()) ? null : v;
        } catch (HL7Exception e) {
            return null;
        }
    }

    private static String value(Type type) {
        if (type instanceof Primitive p) {
            String v = p.getValue();
            return (v == null || v.isBlank()) ? null : v;
        }
        return null;
    }

    private static String primitiveValue(OBX obx) {
        Varies[] values = obx.getObservationValue();
        if (values.length == 0) {
            return null;
        }
        Type data = values[0].getData();
        if (data instanceof Primitive p) {
            return p.getValue();
        }
        try {
            return data != null ? data.encode() : null;
        } catch (HL7Exception e) {
            return null;
        }
    }

    private static AdministrativeGender gender(String hl7Sex) {
        if (hl7Sex == null) {
            return AdministrativeGender.UNKNOWN;
        }
        return switch (hl7Sex.toUpperCase()) {
            case "M" -> AdministrativeGender.MALE;
            case "F" -> AdministrativeGender.FEMALE;
            case "O" -> AdministrativeGender.OTHER;
            default -> AdministrativeGender.UNKNOWN;
        };
    }

    private static Encounter.EncounterStatus encounterStatus(String triggerEvent) {
        // A03 = discharge -> finished; otherwise the patient is in care.
        return "A03".equals(triggerEvent)
                ? Encounter.EncounterStatus.FINISHED
                : Encounter.EncounterStatus.INPROGRESS;
    }

    private static String systemFor(String codingSystem) {
        if (codingSystem == null) {
            return null;
        }
        return switch (codingSystem.toUpperCase()) {
            case "LN", "LOINC" -> "http://loinc.org";
            case "SCT", "SNOMED" -> "http://snomed.info/sct";
            default -> codingSystem;
        };
    }

    private static String parseHl7Date(String hl7) {
        // HL7 dates are YYYYMMDD[HHMM...]; FHIR date wants yyyy-MM-dd.
        String ymd = hl7.length() >= 8 ? hl7.substring(0, 8) : hl7;
        try {
            return new SimpleDateFormat("yyyy-MM-dd")
                    .format(new SimpleDateFormat("yyyyMMdd").parse(ymd));
        } catch (ParseException e) {
            log.warn("Unparseable HL7 date '{}'", hl7);
            return null;
        }
    }

    private static String addEntry(Bundle bundle, Resource resource, String type) {
        String urn = "urn:uuid:" + UUID.randomUUID();
        resource.setId(urn);
        bundle.addEntry()
                .setFullUrl(urn)
                .setResource(resource)
                .getRequest().setMethod(Bundle.HTTPVerb.POST).setUrl(type);
        return urn;
    }
}
