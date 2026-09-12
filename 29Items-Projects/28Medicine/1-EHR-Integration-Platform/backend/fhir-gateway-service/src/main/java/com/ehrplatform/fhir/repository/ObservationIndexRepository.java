package com.ehrplatform.fhir.repository;

import com.ehrplatform.fhir.domain.ObservationIndex;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Repository for the {@link ObservationIndex} projection. */
@Repository
public interface ObservationIndexRepository extends JpaRepository<ObservationIndex, String> {

    /** Backs {@code GET /fhir/Observation?patient=...}. */
    List<ObservationIndex> findByPatientIdOrderByEffectiveAtDesc(String patientId);

    /** Backs {@code GET /fhir/Observation?patient=...&code=system|value}. */
    List<ObservationIndex> findByPatientIdAndCodeSystemAndCodeValue(
            String patientId, String codeSystem, String codeValue);
}
