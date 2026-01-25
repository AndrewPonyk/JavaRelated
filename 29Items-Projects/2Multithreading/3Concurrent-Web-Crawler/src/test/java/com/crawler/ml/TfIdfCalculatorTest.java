package com.crawler.ml;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TfIdfCalculator.
 */
class TfIdfCalculatorTest {

    private TfIdfCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new TfIdfCalculator();
    }

    @Test
    @DisplayName("Should calculate TF correctly for single document")
    void shouldCalculateTfCorrectly() {
        List<String> terms = Arrays.asList("java", "concurrency", "java", "threads");
        Map<String, Double> tf = calculator.calculateTf(terms);

        assertEquals(0.5, tf.get("java"), 0.001); // 2/4
        assertEquals(0.25, tf.get("concurrency"), 0.001); // 1/4
        assertEquals(0.25, tf.get("threads"), 0.001); // 1/4
    }

    @Test
    @DisplayName("Should return zero TF for empty document")
    void shouldReturnZeroTfForEmptyDocument() {
        Map<String, Double> tf = calculator.calculateTf(List.of());
        assertTrue(tf.isEmpty());
    }

    @Test
    @DisplayName("Should calculate IDF correctly for multiple documents")
    void shouldCalculateIdfCorrectly() {
        // Add 3 documents
        calculator.addDocument("doc1", Arrays.asList("java", "concurrency"));
        calculator.addDocument("doc2", Arrays.asList("java", "streams"));
        calculator.addDocument("doc3", Arrays.asList("python", "async"));

        // java appears in 2/3 documents
        double javaIdf = calculator.calculateIdf("java");
        assertEquals(Math.log(3.0 / 2), javaIdf, 0.001);

        // python appears in 1/3 documents (higher IDF = rarer)
        double pythonIdf = calculator.calculateIdf("python");
        assertEquals(Math.log(3.0 / 1), pythonIdf, 0.001);

        assertTrue(pythonIdf > javaIdf); // Rarer terms have higher IDF
    }

    @Test
    @DisplayName("Should return zero IDF for unknown term")
    void shouldReturnZeroIdfForUnknownTerm() {
        calculator.addDocument("doc1", Arrays.asList("java", "concurrency"));
        assertEquals(0.0, calculator.calculateIdf("unknown"));
    }

    @Test
    @DisplayName("Should calculate TF-IDF correctly")
    void shouldCalculateTfIdfCorrectly() {
        calculator.addDocument("doc1", Arrays.asList("java", "java", "concurrency"));
        calculator.addDocument("doc2", Arrays.asList("python", "async"));

        // TF-IDF for "java" in doc1
        // TF = 2/3, IDF = log(2/1) = log(2)
        double expectedTfIdf = (2.0 / 3) * Math.log(2);
        double actualTfIdf = calculator.calculateTfIdf("doc1", "java");

        assertEquals(expectedTfIdf, actualTfIdf, 0.001);
    }

    @Test
    @DisplayName("Should get TF-IDF vector for document")
    void shouldGetTfIdfVector() {
        // Need at least 2 documents for non-zero IDF (IDF = log(N/df), with N=1 gives log(1)=0)
        calculator.addDocument("doc1", Arrays.asList("java", "concurrency", "threads"));
        calculator.addDocument("doc2", Arrays.asList("python", "asyncio")); // Different terms

        Map<String, Double> vector = calculator.getTfIdfVector("doc1");

        assertFalse(vector.isEmpty());
        assertTrue(vector.containsKey("java"));
        assertTrue(vector.containsKey("concurrency"));
        assertTrue(vector.containsKey("threads"));
    }

    @Test
    @DisplayName("Should get top terms by TF-IDF")
    void shouldGetTopTerms() {
        calculator.addDocument("doc1", Arrays.asList("java", "java", "java", "concurrency"));
        calculator.addDocument("doc2", Arrays.asList("python", "django"));

        List<String> topTerms = calculator.getTopTerms("doc1", 2);

        assertEquals(2, topTerms.size());
        // "java" should be first because it has higher TF
        assertEquals("java", topTerms.get(0));
    }

    @Test
    @DisplayName("Should calculate cosine similarity between documents")
    void shouldCalculateCosineSimilarity() {
        calculator.addDocument("doc1", Arrays.asList("java", "concurrency", "threads"));
        calculator.addDocument("doc2", Arrays.asList("java", "concurrency", "streams"));
        calculator.addDocument("doc3", Arrays.asList("python", "async", "await"));

        double sim12 = calculator.cosineSimilarity("doc1", "doc2");
        double sim13 = calculator.cosineSimilarity("doc1", "doc3");

        // doc1 and doc2 share terms, should have higher similarity
        assertTrue(sim12 > sim13);
        assertTrue(sim12 >= 0 && sim12 <= 1);
        assertTrue(sim13 >= 0 && sim13 <= 1);
    }

    @Test
    @DisplayName("Should return zero similarity for unrelated documents")
    void shouldReturnZeroSimilarityForUnrelatedDocs() {
        calculator.addDocument("doc1", Arrays.asList("java", "spring", "hibernate"));
        calculator.addDocument("doc2", Arrays.asList("python", "django", "flask"));

        double similarity = calculator.cosineSimilarity("doc1", "doc2");
        assertEquals(0.0, similarity, 0.001);
    }

    @Test
    @DisplayName("Should track document and term counts")
    void shouldTrackCounts() {
        calculator.addDocument("doc1", Arrays.asList("java", "concurrency"));
        calculator.addDocument("doc2", Arrays.asList("java", "streams"));

        assertEquals(2, calculator.getDocumentCount());
        assertTrue(calculator.getUniqueTermCount() >= 3); // java, concurrency, streams
    }

    @Test
    @DisplayName("Should handle concurrent document additions")
    void shouldHandleConcurrentAdditions() throws InterruptedException {
        int numThreads = 5;
        int docsPerThread = 20;

        Thread[] threads = new Thread[numThreads];
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < docsPerThread; j++) {
                    calculator.addDocument(
                        "doc-" + threadId + "-" + j,
                        Arrays.asList("term" + j, "common", "word" + threadId)
                    );
                }
            });
        }

        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();

        assertEquals(numThreads * docsPerThread, calculator.getDocumentCount());
    }
}
