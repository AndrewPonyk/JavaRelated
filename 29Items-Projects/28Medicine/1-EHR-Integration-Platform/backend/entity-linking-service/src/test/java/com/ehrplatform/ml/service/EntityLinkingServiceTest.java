package com.ehrplatform.ml.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ehrplatform.ml.controller.StratificationDtos.LinkRequest;
import com.ehrplatform.ml.controller.StratificationDtos.LinkResponse;
import com.ehrplatform.ml.controller.StratificationDtos.StratificationRequest;
import com.ehrplatform.ml.controller.StratificationDtos.StratificationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the real cosine-kNN risk stratifier (no mocks — exercises the
 * vectorizer + corpus + scoring together).
 */
class EntityLinkingServiceTest {

    private EntityLinkingService service;

    @BeforeEach
    void setUp() {
        TextVectorizer vectorizer = new TextVectorizer();
        NoteCorpus corpus = new NoteCorpus(vectorizer);
        corpus.seed();
        service = new EntityLinkingService(vectorizer, corpus);
    }

    @Test
    void stratify_highRiskNoteYieldsHighTier() {
        StratificationResponse r = service.stratify(new StratificationRequest(
                "p1", "acute myocardial infarction chest pain elevated troponin", 3));

        assertThat(r.riskTier()).isEqualTo("HIGH");
        assertThat(r.riskScore()).isGreaterThan(0.66);
        assertThat(r.cohort()).isNotEmpty();
    }

    @Test
    void stratify_routineNoteYieldsLowTier() {
        StratificationResponse r = service.stratify(new StratificationRequest(
                "p2", "routine annual checkup healthy normal vital signs", 3));

        assertThat(r.riskTier()).isEqualTo("LOW");
        assertThat(r.riskScore()).isLessThan(0.33);
    }

    @Test
    void stratify_unknownVocabularyYieldsZeroScoreLow() {
        StratificationResponse r = service.stratify(new StratificationRequest(
                "p3", "zzz qqq xyzzy gibberish nonsense", 5));

        assertThat(r.riskScore()).isEqualTo(0.0);
        assertThat(r.riskTier()).isEqualTo("LOW");
        assertThat(r.cohort()).isEmpty();
    }

    @Test
    void link_similarNotesAreCandidates() {
        LinkResponse r = service.link(new LinkRequest(
                "a", "chest pain shortness of breath troponin elevated",
                "b", "elevated troponin chest pain dyspnea"));

        assertThat(r.confidence()).isGreaterThan(0.5);
        assertThat(r.status()).isEqualTo("CANDIDATE");
    }

    @Test
    void link_dissimilarNotesAreRejected() {
        LinkResponse r = service.link(new LinkRequest(
                "a", "routine wellness visit healthy",
                "b", "fractured femur orthopedic surgery"));

        assertThat(r.status()).isEqualTo("REJECTED");
    }
}
