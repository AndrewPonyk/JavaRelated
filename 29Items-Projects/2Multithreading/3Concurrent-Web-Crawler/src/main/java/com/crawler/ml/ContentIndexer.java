package com.crawler.ml;

import com.crawler.db.DatabaseManager;
import com.crawler.db.IndexRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Indexes crawled content for search and relevance scoring.
 *
 * <p>Coordinates:
 * <ul>
 *   <li>Text preprocessing</li>
 *   <li>TF-IDF calculation</li>
 *   <li>Relevance scoring</li>
 *   <li>Index persistence to database</li>
 * </ul>
 */
public class ContentIndexer {

    private static final Logger logger = LoggerFactory.getLogger(ContentIndexer.class);

    private final TextPreprocessor preprocessor;
    private final TfIdfCalculator tfidfCalculator;
    private final RelevanceScorer relevanceScorer;
    private final IndexRepository indexRepository;

    public ContentIndexer(DatabaseManager dbManager) {
        this.preprocessor = new TextPreprocessor();
        this.tfidfCalculator = new TfIdfCalculator();
        this.relevanceScorer = new RelevanceScorer(tfidfCalculator);
        this.indexRepository = new IndexRepository(dbManager);
    }

    /**
     * Index a document.
     *
     * @param url              Document URL (unique identifier)
     * @param title            Page title
     * @param preprocessedText Already preprocessed text content
     * @return Relevance score for the document
     */
    public double index(String url, String title, String preprocessedText) {
        try {
            // Tokenize the preprocessed text
            List<String> terms = preprocessor.tokenize(preprocessedText);

            if (terms.isEmpty()) {
                logger.debug("No terms to index for URL: {}", url);
                return 0.0;
            }

            // Add document to TF-IDF calculator
            tfidfCalculator.addDocument(url, terms);

            // Calculate relevance score
            double relevanceScore = relevanceScorer.score(url, title, preprocessedText, terms);

            // Get TF-IDF vector for storage
            Map<String, Double> tfidfVector = tfidfCalculator.getTfIdfVector(url);

            // Persist to database
            indexRepository.saveIndex(url, tfidfVector, relevanceScore);

            logger.debug("Indexed URL: {} with {} terms, score={:.3f}",
                    url, terms.size(), relevanceScore);

            return relevanceScore;

        } catch (Exception e) {
            logger.error("Error indexing URL: {}", url, e);
            return 0.0;
        }
    }

    /**
     * Index a document with raw text (will preprocess first).
     *
     * @param url       Document URL
     * @param title     Page title
     * @param rawText   Raw text content
     * @return Relevance score
     */
    public double indexRaw(String url, String title, String rawText) {
        String preprocessedText = preprocessor.preprocess(rawText);
        return index(url, title, preprocessedText);
    }

    /**
     * Search the index for documents matching a query.
     *
     * @param query Search query
     * @param limit Maximum number of results
     * @return List of matching document URLs with scores
     */
    public List<SearchResult> search(String query, int limit) {
        // Preprocess and tokenize the query
        String preprocessedQuery = preprocessor.preprocess(query);
        List<String> queryTerms = preprocessor.tokenize(preprocessedQuery);

        if (queryTerms.isEmpty()) {
            logger.debug("No valid terms in query: {}", query);
            return List.of();
        }

        logger.debug("Searching for query '{}' with {} terms", query, queryTerms.size());

        // Search using IndexRepository which:
        // 1. Finds documents containing query terms
        // 2. Scores by summed TF-IDF values
        // 3. Returns results ranked by score
        return indexRepository.search(queryTerms, limit);
    }

    /**
     * Set target keywords for relevance scoring.
     *
     * @param keywords Keywords that indicate relevant content
     */
    public void setTargetKeywords(List<String> keywords) {
        relevanceScorer.setTargetKeywords(keywords);
    }

    /**
     * Record an incoming link for link popularity scoring.
     *
     * @param targetUrl The URL being linked to
     */
    public void recordLink(String targetUrl) {
        relevanceScorer.recordIncomingLink(targetUrl);
    }

    /**
     * Get indexing statistics.
     */
    public String getStats() {
        return String.format("ContentIndexer[%s, %s]",
                tfidfCalculator.getStats(),
                relevanceScorer.getStats());
    }

    /**
     * Search result record.
     */
    public record SearchResult(String url, double score) {
    }
}
