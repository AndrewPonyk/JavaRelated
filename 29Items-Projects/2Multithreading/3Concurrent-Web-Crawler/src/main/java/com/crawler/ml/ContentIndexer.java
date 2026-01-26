package com.crawler.ml;

import com.crawler.db.DatabaseManager;
import com.crawler.db.IndexRepository;
import com.crawler.db.PageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Indexes crawled content for search and relevance scoring.
 *
 * <p>Uses two-pass approach:
 * <ul>
 *   <li>Pass 1 (during crawl): Store terms without scoring</li>
 *   <li>Pass 2 (after crawl): Recalculate all scores with full corpus</li>
 * </ul>
 */
public class ContentIndexer { // |su:119 ML pipeline coordinator: preprocess → TF-IDF → score → persist

    private static final Logger logger = LoggerFactory.getLogger(ContentIndexer.class);

    private final TextPreprocessor preprocessor; // |su:120 Step 1: clean text, tokenize, stem
    private final TfIdfCalculator tfidfCalculator; // |su:121 Step 2: calculate term importance
    private final RelevanceScorer relevanceScorer; // |su:122 Step 3: compute overall relevance
    private final IndexRepository indexRepository; // |su:123 Step 4: persist to database
    private final DatabaseManager dbManager;

    public ContentIndexer(DatabaseManager dbManager) {
        this.dbManager = dbManager;
        this.preprocessor = new TextPreprocessor();
        this.tfidfCalculator = new TfIdfCalculator();
        this.relevanceScorer = new RelevanceScorer(tfidfCalculator);
        this.indexRepository = new IndexRepository(dbManager);
    }

    /**
     * Index a document (Pass 1 - no scoring, just store terms).
     *
     * @param url              Document URL (unique identifier)
     * @param title            Page title
     * @param preprocessedText Already preprocessed text content
     * @return 0.0 (scoring happens in pass 2)
     */
    public double index(String url, String title, String preprocessedText) {
        try {
            // Tokenize the preprocessed text
            List<String> terms = preprocessor.tokenize(preprocessedText);

            if (terms.isEmpty()) {
                logger.debug("No terms to index for URL: {}", url);
                return 0.0;
            }

            // Add document to TF-IDF calculator (builds corpus)
            tfidfCalculator.addDocument(url, terms);

            // Get TF-IDF vector for storage (score=0 for now, will recalculate later)
            Map<String, Double> tfidfVector = tfidfCalculator.getTfIdfVector(url);

            // Persist terms to database with score=0 (pass 1)
            indexRepository.saveIndex(url, tfidfVector, 0.0);

            logger.trace("Indexed URL: {} with {} terms (score pending)", url, terms.size());

            return 0.0; // Actual scoring in pass 2

        } catch (Exception e) {
            logger.error("Error indexing URL: {}", url, e);
            return 0.0;
        }
    }

    /**
     * Recalculate all scores after crawl completes (Pass 2).
     * Uses full corpus for accurate TF-IDF scoring.
     */
    public void recalculateAllScores() {
        logger.info("=== PASS 2: Recalculating scores with full corpus ({} docs) ===",
                tfidfCalculator.getDocumentCount());

        PageRepository pageRepo = new PageRepository(dbManager);
        List<PageRepository.PageData> allPages = pageRepo.findTopByRelevance(Integer.MAX_VALUE);

        int processed = 0;
        int skipped = 0;

        // Collect all updates first
        Map<String, Double> scoreUpdates = new java.util.LinkedHashMap<>();

        for (PageRepository.PageData page : allPages) {
            try {
                // Get terms for this page from TF-IDF cache
                Map<String, Double> tfidfVector = tfidfCalculator.getTfIdfVector(page.url());

                // Create content string with actual length for length scoring
                int contentLen = page.contentLength();
                String contentProxy = contentLen > 0 ? "x".repeat(contentLen) : "";

                // Calculate score - even if no TF-IDF terms, we still score title/length
                List<String> terms = tfidfVector.isEmpty()
                        ? List.of()
                        : tfidfVector.keySet().stream().toList();

                double score = relevanceScorer.score(
                        page.url(),
                        page.title(),
                        contentProxy,
                        terms
                );

                scoreUpdates.put(page.url(), score);
                processed++;

                if (tfidfVector.isEmpty()) {
                    skipped++;
                }

            } catch (Exception e) {
                logger.error("Error recalculating score for: {}", page.url(), e);
            }
        }

        // Batch update all scores in a single transaction
        try {
            dbManager.executeInTransaction(conn -> {
                String sql = "UPDATE pages SET relevance_score = ? WHERE url = ?";
                try (var ps = conn.prepareStatement(sql)) {
                    for (var entry : scoreUpdates.entrySet()) {
                        ps.setDouble(1, entry.getValue());
                        ps.setString(2, entry.getKey());
                        ps.addBatch();
                    }
                    int[] results = ps.executeBatch();
                    logger.info("=== Batch updated {} rows ===", results.length);
                }
            });
        } catch (Exception e) {
            logger.error("Failed to batch update scores", e);
        }

        logger.info("=== Recalculated scores for {} pages ({} without TF-IDF terms) ===",
                processed, skipped);

        // Verify scores were persisted
        List<PageRepository.PageData> topPages = pageRepo.findTopByRelevance(5);
        if (!topPages.isEmpty()) {
            double topScore = topPages.get(0).relevanceScore();
            logger.info("=== Verification: Top page score in DB = {} (URL: {}) ===",
                    topScore, topPages.get(0).url());
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
    public List<SearchResult> search(String query, int limit) { // |su:124 Search: query → preprocess → find matching docs → rank by TF-IDF
        // |su:125 Apply same preprocessing to query as we did to documents
        String preprocessedQuery = preprocessor.preprocess(query);
        List<String> queryTerms = preprocessor.tokenize(preprocessedQuery);

        if (queryTerms.isEmpty()) {
            logger.debug("No valid terms in query: {}", query);
            return List.of();
        }

        logger.debug("Searching for query '{}' with {} terms", query, queryTerms.size());

        // |su:126 DB search: find docs with query terms, sum TF-IDF scores, return top results
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
