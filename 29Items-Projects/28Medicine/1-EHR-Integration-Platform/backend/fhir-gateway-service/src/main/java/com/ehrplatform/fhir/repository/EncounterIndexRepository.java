package com.ehrplatform.fhir.repository;

import com.ehrplatform.fhir.domain.EncounterIndex;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Repository for the {@link EncounterIndex} projection. */
@Repository
public interface EncounterIndexRepository extends JpaRepository<EncounterIndex, String> {

    /** Backs {@code GET /fhir/Encounter?patient=...}. */
    List<EncounterIndex> findByPatientIdOrderByPeriodStartDesc(String patientId);
}
