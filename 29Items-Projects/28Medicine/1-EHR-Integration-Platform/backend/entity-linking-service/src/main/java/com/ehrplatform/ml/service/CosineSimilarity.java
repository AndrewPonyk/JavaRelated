package com.ehrplatform.ml.service;

import java.util.Map;

/** Cosine similarity between two sparse term-frequency vectors. */
public final class CosineSimilarity {

    private CosineSimilarity() {
    }

    /** @return similarity in [0,1]; 0 if either vector is empty. */
    public static double between(Map<String, Double> a, Map<String, Double> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        // Iterate the smaller map for the dot product.
        Map<String, Double> smaller = a.size() <= b.size() ? a : b;
        Map<String, Double> larger = smaller == a ? b : a;

        double dot = 0.0;
        for (Map.Entry<String, Double> e : smaller.entrySet()) {
            Double other = larger.get(e.getKey());
            if (other != null) {
                dot += e.getValue() * other;
            }
        }
        double norm = Math.sqrt(norm2(a)) * Math.sqrt(norm2(b));
        return norm == 0.0 ? 0.0 : dot / norm;
    }

    private static double norm2(Map<String, Double> v) {
        double sum = 0.0;
        for (double value : v.values()) {
            sum += value * value;
        }
        return sum;
    }
}
