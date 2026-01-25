package com.crawler.ml;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Text preprocessing for ML and indexing operations.
 *
 * <p>Preprocessing steps:
 * <ul>
 *   <li>Lowercase conversion</li>
 *   <li>Punctuation removal</li>
 *   <li>Stop word removal</li>
 *   <li>Simple stemming (suffix stripping)</li>
 *   <li>Minimum length filtering</li>
 * </ul>
 */
public class TextPreprocessor {

    private static final Logger logger = LoggerFactory.getLogger(TextPreprocessor.class);

    // Minimum word length to keep
    private static final int MIN_WORD_LENGTH = 3;

    // Pattern for splitting text into tokens
    private static final Pattern TOKENIZE_PATTERN = Pattern.compile("[\\s\\p{Punct}]+");

    // Pattern for non-alphabetic characters
    private static final Pattern NON_ALPHA_PATTERN = Pattern.compile("[^a-z]");

    // Common English stop words
    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "and", "are", "as", "at", "be", "by", "for", "from",
            "has", "he", "in", "is", "it", "its", "of", "on", "that", "the",
            "to", "was", "were", "will", "with", "this", "but", "they",
            "have", "had", "what", "when", "where", "who", "which", "why",
            "how", "all", "each", "every", "both", "few", "more", "most",
            "other", "some", "such", "than", "too", "very", "can", "just",
            "should", "now", "been", "being", "would", "could", "also",
            "into", "only", "your", "our", "their", "not", "you", "we"
    );

    // Common English suffixes for simple stemming
    private static final String[] SUFFIXES = {
            "ing", "ed", "ly", "er", "est", "tion", "ness", "ment", "able", "ible"
    };

    /**
     * Full preprocessing pipeline.
     *
     * @param text Raw text input
     * @return Preprocessed text string
     */
    public String preprocess(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        // Convert to lowercase
        String processed = text.toLowerCase();

        // Remove URLs
        processed = processed.replaceAll("https?://\\S+", " ");

        // Remove email addresses
        processed = processed.replaceAll("\\S+@\\S+", " ");

        // Remove numbers
        processed = processed.replaceAll("\\d+", " ");

        // Normalize whitespace
        processed = processed.replaceAll("\\s+", " ").trim();

        return processed;
    }

    /**
     * Tokenize preprocessed text into individual terms.
     *
     * @param text Preprocessed text
     * @return List of tokens/terms
     */
    public List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }

        List<String> tokens = new ArrayList<>();
        String[] words = TOKENIZE_PATTERN.split(text);

        for (String word : words) {
            // Remove non-alphabetic characters
            word = NON_ALPHA_PATTERN.matcher(word).replaceAll("");

            // Skip short words
            if (word.length() < MIN_WORD_LENGTH) {
                continue;
            }

            // Skip stop words
            if (STOP_WORDS.contains(word)) {
                continue;
            }

            // Apply simple stemming
            String stemmed = stem(word);

            // Skip if too short after stemming
            if (stemmed.length() >= MIN_WORD_LENGTH) {
                tokens.add(stemmed);
            }
        }

        return tokens;
    }

    /**
     * Simple suffix-stripping stemmer.
     * Note: For production, consider using Porter Stemmer or similar.
     *
     * @param word Word to stem
     * @return Stemmed word
     */
    public String stem(String word) {
        if (word.length() <= 5) {
            return word;
        }

        for (String suffix : SUFFIXES) {
            if (word.endsWith(suffix) && word.length() > suffix.length() + 3) {
                return word.substring(0, word.length() - suffix.length());
            }
        }

        return word;
    }

    /**
     * Extract n-grams from tokenized text.
     *
     * @param tokens List of tokens
     * @param n      Size of n-grams (2 for bigrams, 3 for trigrams)
     * @return List of n-gram strings
     */
    public List<String> extractNgrams(List<String> tokens, int n) {
        if (tokens.size() < n) {
            return Collections.emptyList();
        }

        List<String> ngrams = new ArrayList<>();
        for (int i = 0; i <= tokens.size() - n; i++) {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < n; j++) {
                if (j > 0) sb.append("_");
                sb.append(tokens.get(i + j));
            }
            ngrams.add(sb.toString());
        }

        return ngrams;
    }

    /**
     * Get term frequency map for a list of tokens.
     *
     * @param tokens List of tokens
     * @return Map of term -> count
     */
    public Map<String, Integer> getTermFrequency(List<String> tokens) {
        Map<String, Integer> freq = new HashMap<>();
        for (String token : tokens) {
            freq.merge(token, 1, Integer::sum);
        }
        return freq;
    }

    /**
     * Check if a word is a stop word.
     *
     * @param word Word to check
     * @return true if stop word
     */
    public boolean isStopWord(String word) {
        return STOP_WORDS.contains(word.toLowerCase());
    }
}
