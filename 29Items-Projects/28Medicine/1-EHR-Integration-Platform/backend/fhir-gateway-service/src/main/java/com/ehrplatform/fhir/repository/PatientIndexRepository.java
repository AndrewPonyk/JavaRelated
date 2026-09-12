package com.ehrplatform.fhir.repository;

import com.ehrplatform.fhir.domain.PatientIndex;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link PatientIndex} projection.
 *
 * <p>Owned exclusively by the FHIR gateway (database-per-service). No other
 * service touches this schema directly — they consume events instead.
 */
@Repository
public interface PatientIndexRepository extends JpaRepository<PatientIndex, String> {

    /** Backs {@code GET /fhir/Patient?identifier=system|value}. */
    List<PatientIndex> findByIdentifierSystemAndIdentifierValue(String system, String value);

    /** Backs {@code GET /fhir/Patient?identifier=value} (any system). */
    List<PatientIndex> findByIdentifierValue(String value);

    /** Backs {@code GET /fhir/Patient?family=...} (case-insensitive contains). */
    @Query("select p from PatientIndex p where upper(p.familyName) like upper(concat('%', :family, '%'))")
    List<PatientIndex> searchByFamily(@Param("family") String family);
}
