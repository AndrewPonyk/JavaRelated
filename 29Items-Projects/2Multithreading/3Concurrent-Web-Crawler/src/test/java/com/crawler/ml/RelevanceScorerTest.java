package com.crawler.ml;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RelevanceScorer.
 */
class RelevanceScorerTest {

    private TfIdfCalculator tfidfCalculator;
    private RelevanceScorer scorer;

    @BeforeEach
    void setUp() {
        tfidfCalculator = new TfIdfCalculator();
        scorer = new RelevanceScorer(tfidfCalculator);
    }

    @Test
    @DisplayName("Should score document without target keywords")
    void shouldScoreWithoutTargetKeywords() {
        List<String> terms = Arrays.asList("java", "concurrency", "threads", "executor");
        tfidfCalculator.addDocument("doc1", terms);

        double score = scorer.score("doc1", "Java Concurrency Guide", "java concurrency guide text", terms);

        assertTrue(score >= 0.0 && score <= 1.0);
    }

    @Test
    @DisplayName("Should score higher for matching target keywords")
    void shouldScoreHigherForMatchingKeywords() {
        List<String> terms1 = Arrays.asList("java", "concurrency", "threads");
        List<String> terms2 = Arrays.asList("python", "async", "await");

        tfidfCalculator.addDocument("doc1", terms1);
        tfidfCalculator.addDocument("doc2", terms2);

        scorer.setTargetKeywords(Arrays.asList("java", "concurrency"));

        double score1 = scorer.score("doc1", "Java Guide", "java concurrency content", terms1);
        double score2 = scorer.score("doc2", "Python Guide", "python async content", terms2);

        assertTrue(score1 > score2);
    }

    @Test
    @DisplayName("Should factor in title relevance")
    void shouldFactorInTitleRelevance() {
        scorer.setTargetKeywords(Arrays.asList("java"));

        List<String> terms = Arrays.asList("java", "programming");
        tfidfCalculator.addDocument("doc1", terms);

        double scoreWithKeywordInTitle = scorer.score(
            "doc1", "Java Programming Tutorial", "content about programming", terms
        );

        tfidfCalculator.addDocument("doc2", terms);
        double scoreWithoutKeywordInTitle = scorer.score(
            "doc2", "Programming Tutorial", "content about programming", terms
        );

        assertTrue(scoreWithKeywordInTitle >= scoreWithoutKeywordInTitle);
    }

    @Test
    @DisplayName("Should factor in content length")
    void shouldFactorInContentLength() {
        List<String> terms = Arrays.asList("test", "content");
        tfidfCalculator.addDocument("short", terms);
        tfidfCalculator.addDocument("optimal", terms);

        // Very short content
        double shortScore = scorer.score("short", "Title", "x".repeat(100), terms);

        // Optimal length content
        double optimalScore = scorer.score("optimal", "Title", "x".repeat(2000), terms);

        assertTrue(optimalScore > shortScore);
    }

    @Test
    @DisplayName("Should track incoming links")
    void shouldTrackIncomingLinks() {
        scorer.recordIncomingLink("https://example.com/popular");
        scorer.recordIncomingLink("https://example.com/popular");
        scorer.recordIncomingLink("https://example.com/popular");
        scorer.recordIncomingLink("https://example.com/unpopular");

        // The link score is internal, but we can verify the stats
        String stats = scorer.getStats();
        assertTrue(stats.contains("trackedUrls=2"));
    }

    @Test
    @DisplayName("Should return score in valid range")
    void shouldReturnScoreInValidRange() {
        List<String> terms = Arrays.asList("word", "another", "term");
        tfidfCalculator.addDocument("doc", terms);

        double score = scorer.score("doc", "Title", "Some content here", terms);

        assertTrue(score >= 0.0);
        assertTrue(score <= 1.0);
    }

    @Test
    @DisplayName("Should handle empty content")
    void shouldHandleEmptyContent() {
        List<String> emptyTerms = List.of();
        tfidfCalculator.addDocument("empty", emptyTerms);

        double score = scorer.score("empty", "", "", emptyTerms);

        assertEquals(0.0, score, 0.001);
    }

    @Test
    @DisplayName("Should handle null title")
    void shouldHandleNullTitle() {
        List<String> terms = Arrays.asList("java", "code");
        tfidfCalculator.addDocument("doc", terms);

        // Should not throw
        double score = scorer.score("doc", null, "java code content", terms);

        assertTrue(score >= 0.0 && score <= 1.0);
    }

    @Test
    @DisplayName("Should update target keywords")
    void shouldUpdateTargetKeywords() {
        scorer.setTargetKeywords(Arrays.asList("java", "spring"));
        String stats = scorer.getStats();
        assertTrue(stats.contains("targetKeywords=2"));

        scorer.setTargetKeywords(Arrays.asList("python"));
        stats = scorer.getStats();
        assertTrue(stats.contains("targetKeywords=1"));
    }
}
