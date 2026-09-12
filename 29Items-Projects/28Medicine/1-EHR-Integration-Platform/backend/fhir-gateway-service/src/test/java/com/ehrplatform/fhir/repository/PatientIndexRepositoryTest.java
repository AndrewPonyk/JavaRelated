package com.ehrplatform.fhir.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ehrplatform.fhir.domain.PatientIndex;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Repository slice test against in-memory H2 (real JPA, real SQL).
 */
@ExtendWith(SpringExtension.class)
@DataJpaTest
@ActiveProfiles("test")
class PatientIndexRepositoryTest {

    @Autowired
    private PatientIndexRepository repository;

    private PatientIndex sample(String id, String system, String value, String family) {
        PatientIndex p = new PatientIndex(id);
        p.setIdentifierSystem(system);
        p.setIdentifierValue(value);
        p.setFamilyName(family);
        p.setGivenName("Test");
        p.setLastUpdated(Instant.now());
        return p;
    }

    @Test
    void findByIdentifierSystemAndValue() {
        repository.save(sample("1", "urn:mrn", "A100", "Smith"));

        var result = repository.findByIdentifierSystemAndIdentifierValue("urn:mrn", "A100");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFamilyName()).isEqualTo("Smith");
    }

    @Test
    void findByIdentifierValueAnySystem() {
        repository.save(sample("2", "urn:other", "B200", "Jones"));

        assertThat(repository.findByIdentifierValue("B200")).hasSize(1);
    }

    @Test
    void searchByFamilyIsCaseInsensitiveContains() {
        repository.save(sample("3", "urn:mrn", "C300", "Andersson"));

        assertThat(repository.searchByFamily("anders")).hasSize(1);
        assertThat(repository.searchByFamily("XXXX")).isEmpty();
    }
}
