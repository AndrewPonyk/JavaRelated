package com.crawler.ml;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TextPreprocessor.
 */
class TextPreprocessorTest {

    private TextPreprocessor preprocessor;

    @BeforeEach
    void setUp() {
        preprocessor = new TextPreprocessor();
    }

    @Test
    @DisplayName("Should preprocess text by lowercasing")
    void shouldLowercaseText() {
        String result = preprocessor.preprocess("HELLO World");
        assertEquals("hello world", result);
    }

    @Test
    @DisplayName("Should remove URLs from text")
    void shouldRemoveUrls() {
        String result = preprocessor.preprocess("Visit https://example.com for more info");
        assertFalse(result.contains("https://"));
        assertFalse(result.contains("example.com"));
    }

    @Test
    @DisplayName("Should remove email addresses")
    void shouldRemoveEmails() {
        String result = preprocessor.preprocess("Contact test@example.com for help");
        assertFalse(result.contains("@"));
    }

    @Test
    @DisplayName("Should remove numbers")
    void shouldRemoveNumbers() {
        String result = preprocessor.preprocess("There are 123 items in 2024");
        assertFalse(result.matches(".*\\d.*"));
    }

    @Test
    @DisplayName("Should normalize whitespace")
    void shouldNormalizeWhitespace() {
        String result = preprocessor.preprocess("Hello    World\n\tTest");
        assertEquals("hello world test", result);
    }

    @Test
    @DisplayName("Should handle null input")
    void shouldHandleNullInput() {
        assertEquals("", preprocessor.preprocess(null));
    }

    @Test
    @DisplayName("Should handle empty input")
    void shouldHandleEmptyInput() {
        assertEquals("", preprocessor.preprocess(""));
        assertEquals("", preprocessor.preprocess("   "));
    }

    @Test
    @DisplayName("Should tokenize text into words")
    void shouldTokenizeText() {
        List<String> tokens = preprocessor.tokenize("java concurrency programming");

        assertEquals(3, tokens.size());
        assertTrue(tokens.contains("java"));
        assertTrue(tokens.contains("concurrency")); // "concurrency" doesn't match any suffix
        assertTrue(tokens.contains("programm")); // stemmed from "programming"
    }

    @Test
    @DisplayName("Should remove stop words")
    void shouldRemoveStopWords() {
        List<String> tokens = preprocessor.tokenize("the java and python are programming languages");

        assertFalse(tokens.contains("the"));
        assertFalse(tokens.contains("and"));
        assertFalse(tokens.contains("are"));
        assertTrue(tokens.contains("java"));
        assertTrue(tokens.contains("python"));
    }

    @Test
    @DisplayName("Should filter short words")
    void shouldFilterShortWords() {
        List<String> tokens = preprocessor.tokenize("a an the java is in programming");

        // Words shorter than 3 chars should be filtered
        assertTrue(tokens.stream().allMatch(t -> t.length() >= 3));
    }

    @Test
    @DisplayName("Should stem words")
    void shouldStemWords() {
        assertEquals("programm", preprocessor.stem("programming"));
        assertEquals("connect", preprocessor.stem("connected"));
        assertEquals("happi", preprocessor.stem("happiness"));
    }

    @Test
    @DisplayName("Should not stem short words")
    void shouldNotStemShortWords() {
        assertEquals("java", preprocessor.stem("java"));
        assertEquals("code", preprocessor.stem("code"));
    }

    @Test
    @DisplayName("Should check stop words")
    void shouldCheckStopWords() {
        assertTrue(preprocessor.isStopWord("the"));
        assertTrue(preprocessor.isStopWord("and"));
        assertTrue(preprocessor.isStopWord("is"));
        assertFalse(preprocessor.isStopWord("java"));
        assertFalse(preprocessor.isStopWord("programming"));
    }

    @Test
    @DisplayName("Should extract bigrams")
    void shouldExtractBigrams() {
        List<String> tokens = List.of("java", "concurrent", "programming");
        List<String> bigrams = preprocessor.extractNgrams(tokens, 2);

        assertEquals(2, bigrams.size());
        assertEquals("java_concurrent", bigrams.get(0));
        assertEquals("concurrent_programming", bigrams.get(1));
    }

    @Test
    @DisplayName("Should extract trigrams")
    void shouldExtractTrigrams() {
        List<String> tokens = List.of("java", "concurrent", "programming", "guide");
        List<String> trigrams = preprocessor.extractNgrams(tokens, 3);

        assertEquals(2, trigrams.size());
        assertEquals("java_concurrent_programming", trigrams.get(0));
        assertEquals("concurrent_programming_guide", trigrams.get(1));
    }

    @Test
    @DisplayName("Should return empty ngrams for short list")
    void shouldReturnEmptyNgramsForShortList() {
        List<String> tokens = List.of("java");
        List<String> bigrams = preprocessor.extractNgrams(tokens, 2);

        assertTrue(bigrams.isEmpty());
    }

    @Test
    @DisplayName("Should calculate term frequency")
    void shouldCalculateTermFrequency() {
        List<String> tokens = List.of("java", "java", "python", "java", "python");
        Map<String, Integer> freq = preprocessor.getTermFrequency(tokens);

        assertEquals(3, freq.get("java"));
        assertEquals(2, freq.get("python"));
    }
}
