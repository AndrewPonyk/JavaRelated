package com.crawler.core;

import com.crawler.ml.ContentIndexer;
import com.crawler.ml.TextPreprocessor;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Processes page content for indexing and analysis.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Extract text content from HTML</li>
 *   <li>Calculate content hash for deduplication</li>
 *   <li>Send content to indexer for TF-IDF calculation</li>
 * </ul>
 */
public class ContentProcessor { // |su:113 Processes HTML: extracts text, hashes content, triggers ML indexing

    private static final Logger logger = LoggerFactory.getLogger(ContentProcessor.class);

    private final ContentIndexer indexer; // |su:114 Handles TF-IDF calculation and relevance scoring
    private final TextPreprocessor preprocessor;

    public ContentProcessor(ContentIndexer indexer) {
        this.indexer = indexer;
        this.preprocessor = new TextPreprocessor();
    }

    /**
     * Process a fetched document.
     *
     * @param document The parsed HTML document
     * @param url      The URL of the document
     * @return ProcessedContent with extracted data
     */
    public ProcessedContent process(Document document, String url) {
        if (document == null) {
            return null;
        }

        try {
            // Extract title
            String title = document.title();

            // Extract main text content
            String textContent = extractText(document);

            // Calculate content hash
            String contentHash = calculateHash(textContent);

            // Preprocess text for indexing
            String preprocessedText = preprocessor.preprocess(textContent);

            // Index the content
            double relevanceScore = indexer.index(url, title, preprocessedText);

            ProcessedContent result = new ProcessedContent(
                    url, title, textContent, contentHash, relevanceScore
            );

            logger.trace("Processed content: url={}, title={}, hash={}",
                    url, title, contentHash.substring(0, 8));

            return result;

        } catch (Exception e) {
            logger.error("Error processing content for URL: {}", url, e);
            return null;
        }
    }

    /**
     * Extract readable text content from HTML document.
     */
    private String extractText(Document document) { // |su:115 Extract readable content only - remove boilerplate
        // |su:116 Remove non-content elements: scripts, styles, navigation, footers
        document.select("script, style, nav, footer, header, aside").remove();

        // Get text from body, or whole document if no body
        String text = document.body() != null ? document.body().text() : document.text();

        // Clean up whitespace
        return text.replaceAll("\\s+", " ").trim();
    }

    /**
     * Calculate SHA-256 hash of content for deduplication.
     */
    private String calculateHash(String content) { // |su:117 SHA-256 hash: detect duplicate content even at different URLs
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes());
            return HexFormat.of().formatHex(hash); // |su:118 Hex string: 64 chars representing 256 bits
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is always available
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Result of content processing.
     */
    public record ProcessedContent(
            String url,
            String title,
            String textContent,
            String contentHash,
            double relevanceScore
    ) {
    }
}
