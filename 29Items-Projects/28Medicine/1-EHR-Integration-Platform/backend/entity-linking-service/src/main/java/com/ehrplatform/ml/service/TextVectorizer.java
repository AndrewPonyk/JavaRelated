package com.ehrplatform.ml.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Turns free-text clinical notes into bag-of-words term-frequency vectors.
 *
 * <p>A deliberately simple, dependency-free vectorizer: lowercase, split on
 * non-letters, drop very short tokens and stopwords, count term frequencies.
 * Good enough to power cosine-similarity kNN over notes; swap for real
 * embeddings (the remote ML server) when available.
 */
@Component
public class TextVectorizer {

    private static final Set<String> STOPWORDS = Set.of(
            "the", "and", "for", "with", "was", "are", "has", "had", "her", "his",
            "she", "him", "they", "this", "that", "from", "have", "not", "but",
            "patient", "history", "reports", "denies", "presents");

    public Map<String, Double> vectorize(String text) {
        Map<String, Double> tf = new HashMap<>();
        if (text == null || text.isBlank()) {
            return tf;
        }
        for (String token : text.toLowerCase().split("[^a-z]+")) {
            if (token.length() <= 2 || STOPWORDS.contains(token)) {
                continue;
            }
            tf.merge(token, 1.0, Double::sum);
        }
        return tf;
    }
}
