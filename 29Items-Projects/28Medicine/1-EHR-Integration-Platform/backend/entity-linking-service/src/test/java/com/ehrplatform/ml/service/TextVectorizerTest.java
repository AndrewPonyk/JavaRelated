package com.ehrplatform.ml.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Map;
import org.junit.jupiter.api.Test;

class TextVectorizerTest {

    private final TextVectorizer vectorizer = new TextVectorizer();

    @Test
    void vectorize_countsTermsDropsStopwordsAndShortTokens() {
        Map<String, Double> v = vectorizer.vectorize("The patient has chest chest pain");

        assertThat(v).containsEntry("chest", 2.0).containsEntry("pain", 1.0);
        assertThat(v).doesNotContainKey("the");   // stopword
        assertThat(v).doesNotContainKey("has");   // stopword
    }

    @Test
    void vectorize_emptyTextYieldsEmptyVector() {
        assertThat(vectorizer.vectorize("")).isEmpty();
        assertThat(vectorizer.vectorize(null)).isEmpty();
    }

    @Test
    void cosine_identicalVectorsAreOne() {
        Map<String, Double> a = vectorizer.vectorize("chest pain troponin elevated");
        assertThat(CosineSimilarity.between(a, a)).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void cosine_disjointVectorsAreZero() {
        Map<String, Double> a = vectorizer.vectorize("chest pain");
        Map<String, Double> b = vectorizer.vectorize("fracture femur");
        assertThat(CosineSimilarity.between(a, b)).isEqualTo(0.0);
    }
}
