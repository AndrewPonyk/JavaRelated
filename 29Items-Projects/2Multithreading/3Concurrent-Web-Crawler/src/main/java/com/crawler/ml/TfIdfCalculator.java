package com.crawler.ml;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Calculates TF-IDF (Term Frequency - Inverse Document Frequency) scores.
 *
 * <p>TF-IDF formula:
 * <ul>
 *   <li>TF(t,d) = (number of times term t appears in document d) / (total terms in d)</li>
 *   <li>IDF(t) = log(total documents / documents containing term t)</li>
 *   <li>TF-IDF(t,d) = TF(t,d) * IDF(t)</li>
 * </ul>
 */
public class TfIdfCalculator { // |su:68 TF-IDF: Term Frequency × Inverse Document Frequency scoring

    private static final Logger logger = LoggerFactory.getLogger(TfIdfCalculator.class);

    private final ConcurrentHashMap<String, AtomicInteger> documentFrequency;

    private final AtomicInteger documentCount;

    // Cache of computed TF vectors per document
    private final ConcurrentHashMap<String, Map<String, Double>> tfCache;

    public TfIdfCalculator() {
        this.documentFrequency = new ConcurrentHashMap<>();
        this.documentCount = new AtomicInteger(0);
        this.tfCache = new ConcurrentHashMap<>();
    }

    /**
     * Add a document to the corpus for IDF calculation.
     *
     * @param documentId Unique identifier for the document
     * @param terms      List of preprocessed terms in the document
     */
    public void addDocument(String documentId, List<String> terms) {
        // Count document
        documentCount.incrementAndGet();

        // Get unique terms in this document
        Set<String> uniqueTerms = new HashSet<>(terms);

        // Update document frequency for each unique term
        for (String term : uniqueTerms) {
            documentFrequency.computeIfAbsent(term, k -> new AtomicInteger(0))
                    .incrementAndGet();
        }

        // Calculate and cache TF for this document
        Map<String, Double> tf = calculateTf(terms);
        tfCache.put(documentId, tf);

        logger.trace("Added document {} with {} unique terms", documentId, uniqueTerms.size());
    }

    /**
     * Calculate Term Frequency for a document.
     *
     * @param terms List of terms in the document
     * @return Map of term -> TF score
     */
    public Map<String, Double> calculateTf(List<String> terms) {
        Map<String, Double> tf = new HashMap<>();

        if (terms.isEmpty()) {
            return tf;
        }

        // Count term occurrences
        Map<String, Integer> termCounts = new HashMap<>();
        for (String term : terms) {
            termCounts.merge(term, 1, Integer::sum);
        }

        // Calculate TF = count / total terms
        double totalTerms = terms.size();
        for (Map.Entry<String, Integer> entry : termCounts.entrySet()) {
            tf.put(entry.getKey(), entry.getValue() / totalTerms);
        }

        return tf;
    }

    /**
     * Calculate Inverse Document Frequency for a term.
     *
     * @param term The term to calculate IDF for
     * @return IDF score
     */
    public double calculateIdf(String term) {
        int totalDocs = documentCount.get();
        if (totalDocs == 0) {
            return 0.0;
        }

        AtomicInteger docFreq = documentFrequency.get(term);
        if (docFreq == null || docFreq.get() == 0) {
            return 0.0;
        }

        return Math.log((double) totalDocs / docFreq.get()); // |su:24 IDF = log(N/df) - rare terms score higher
    }

    /**
     * Calculate TF-IDF score for a term in a document.
     *
     * @param documentId The document identifier
     * @param term       The term to score
     * @return TF-IDF score
     */
    public double calculateTfIdf(String documentId, String term) {
        Map<String, Double> tf = tfCache.get(documentId);
        if (tf == null || !tf.containsKey(term)) {
            return 0.0;
        }

        return tf.get(term) * calculateIdf(term);
    }

    /**
     * Get TF-IDF vector for a document.
     *
     * @param documentId The document identifier
     * @return Map of term -> TF-IDF score
     */
    public Map<String, Double> getTfIdfVector(String documentId) {
        Map<String, Double> tf = tfCache.get(documentId);
        if (tf == null) {
            return Collections.emptyMap();
        }

        Map<String, Double> tfidf = new HashMap<>();
        for (String term : tf.keySet()) {
            double score = calculateTfIdf(documentId, term);
            if (score > 0) {
                tfidf.put(term, score);
            }
        }

        return tfidf;
    }

    /**
     * Get the top N terms by TF-IDF score for a document.
     *
     * @param documentId The document identifier
     * @param n          Number of terms to return
     * @return List of terms sorted by TF-IDF score descending
     */
    public List<String> getTopTerms(String documentId, int n) {
        Map<String, Double> tfidf = getTfIdfVector(documentId);

        return tfidf.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(n)
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * Calculate cosine similarity between two documents.
     *
     * @param docId1 First document ID
     * @param docId2 Second document ID
     * @return Cosine similarity score (0 to 1)
     */
    public double cosineSimilarity(String docId1, String docId2) { // |su:73 Cosine similarity: measures document similarity (0=different, 1=identical)
        Map<String, Double> vec1 = getTfIdfVector(docId1);
        Map<String, Double> vec2 = getTfIdfVector(docId2);

        if (vec1.isEmpty() || vec2.isEmpty()) {
            return 0.0;
        }

        double dotProduct = 0.0;
        for (Map.Entry<String, Double> entry : vec1.entrySet()) {
            Double val2 = vec2.get(entry.getKey());
            if (val2 != null) {
                dotProduct += entry.getValue() * val2;
            }
        }

        double mag1 = Math.sqrt(vec1.values().stream().mapToDouble(v -> v * v).sum());
        double mag2 = Math.sqrt(vec2.values().stream().mapToDouble(v -> v * v).sum());

        if (mag1 == 0 || mag2 == 0) {
            return 0.0;
        }

        return dotProduct / (mag1 * mag2);
    }

    /**
     * Get current corpus statistics.
     */
    public String getStats() {
        return String.format("TfIdf[documents=%d, uniqueTerms=%d]",
                documentCount.get(), documentFrequency.size());
    }

    /**
     * Get the total number of documents in the corpus.
     */
    public int getDocumentCount() {
        return documentCount.get();
    }

    /**
     * Get the number of unique terms in the corpus.
     */
    public int getUniqueTermCount() {
        return documentFrequency.size();
    }
}
