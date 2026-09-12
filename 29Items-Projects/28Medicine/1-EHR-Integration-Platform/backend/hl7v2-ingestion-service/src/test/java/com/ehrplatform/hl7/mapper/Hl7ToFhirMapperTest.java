package com.ehrplatform.hl7.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.uhn.hl7v2.model.Message;
import com.ehrplatform.common.exception.ValidationException;
import com.ehrplatform.hl7.parser.Hl7v2Parser;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Enumerations.AdministrativeGender;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.Test;

class Hl7ToFhirMapperTest {

    private final Hl7v2Parser parser = new Hl7v2Parser();
    private final Hl7ToFhirMapper mapper = new Hl7ToFhirMapper();

    private static String hl7(String... segments) {
        return String.join("\r", segments);
    }

    private static final String ADT_A01 = hl7(
            "MSH|^~\\&|SENDING_APP|SENDING_FAC|REC_APP|REC_FAC|20240101120000||ADT^A01|MSG1|P|2.5",
            "EVN|A01|20240101120000",
            "PID|1||MRN12345^^^HOSP^MR||Doe^John^A||19800101|M|||123 Main St^^Town^ST^12345",
            "PV1|1|I|ICU^101^A");

    private static final String ORU_R01 = hl7(
            "MSH|^~\\&|LAB|FAC|REC|REC|20240101120000||ORU^R01|MSG2|P|2.5",
            "PID|1||MRN999^^^HOSP^MR||Smith^Jane||19750505|F",
            "OBR|1|||CBC^Complete Blood Count",
            "OBX|1|NM|8867-4^Heart rate^LN||72|bpm|||||F",
            "OBX|2|NM|2339-0^Glucose^LN||95|mg/dL|||||F");

    @Test
    void mapsAdtToPatientAndEncounter() {
        Message message = parser.parse(ADT_A01);

        Bundle bundle = mapper.toFhirBundle(message);

        Patient patient = resourceOfType(bundle, Patient.class);
        assertThat(patient.getNameFirstRep().getFamily()).isEqualTo("Doe");
        assertThat(patient.getNameFirstRep().getGivenAsSingleString()).contains("John");
        assertThat(patient.getGender()).isEqualTo(AdministrativeGender.MALE);
        assertThat(patient.getIdentifierFirstRep().getValue()).isEqualTo("MRN12345");
        assertThat(patient.getBirthDateElement().getValueAsString()).isEqualTo("1980-01-01");

        Encounter encounter = resourceOfType(bundle, Encounter.class);
        assertThat(encounter.getClass_().getCode()).isEqualTo("I");
        assertThat(encounter.getSubject().getReference()).isEqualTo(patient.getId());
    }

    @Test
    void mapsOruToObservations() {
        Message message = parser.parse(ORU_R01);

        Bundle bundle = mapper.toFhirBundle(message);

        long observations = bundle.getEntry().stream()
                .filter(e -> e.getResource() instanceof Observation).count();
        assertThat(observations).isEqualTo(2);

        Observation first = resourceOfType(bundle, Observation.class);
        assertThat(first.getCode().getCodingFirstRep().getCode()).isEqualTo("8867-4");
        assertThat(first.getCode().getCodingFirstRep().getSystem()).isEqualTo("http://loinc.org");
        assertThat(first.getValueStringType().getValue()).isEqualTo("72");
    }

    @Test
    void rejectsUnsupportedMessageType() {
        // A valid-enough MSH with an unsupported type (SIU scheduling).
        Message message = parser.parse(hl7(
                "MSH|^~\\&|A|B|C|D|20240101||SIU^S12|M9|P|2.5",
                "SCH|1|2"));

        assertThatThrownBy(() -> mapper.toFhirBundle(message))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Unsupported");
    }

    @Test
    void rejectsAdtWithoutPatientName() {
        Message message = parser.parse(hl7(
                "MSH|^~\\&|A|B|C|D|20240101||ADT^A01|M10|P|2.5",
                "PID|1||MRN^^^HOSP^MR||"));

        assertThatThrownBy(() -> mapper.toFhirBundle(message))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("PID-5");
    }

    @SuppressWarnings("unchecked")
    private static <T extends Resource> T resourceOfType(Bundle bundle, Class<T> type) {
        return (T) bundle.getEntry().stream()
                .map(Bundle.BundleEntryComponent::getResource)
                .filter(type::isInstance)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No " + type.getSimpleName() + " in bundle"));
    }
}
