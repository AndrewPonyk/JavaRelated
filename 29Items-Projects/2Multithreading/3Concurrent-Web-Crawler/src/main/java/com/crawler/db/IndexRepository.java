package com.crawler.db;

import com.crawler.ml.ContentIndexer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Repository for TF-IDF index storage and search.
 */
public class IndexRepository {

    private static final Logger logger = LoggerFactory.getLogger(IndexRepository.class);

    private final DatabaseManager dbManager;

    public IndexRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    /**
     * Save TF-IDF index for a page.
     *
     * @param url            Page URL
     * @param tfidfVector    Map of term -> TF-IDF score
     * @param relevanceScore Overall relevance score
     */
    public void saveIndex(String url, Map<String, Double> tfidfVector, double relevanceScore) {
        try {
            dbManager.executeInTransaction(conn -> {
                // Get or create page ID
                long pageId = getOrCreatePageId(conn, url);
                if (pageId < 0) {
                    return;
                }

                // Update relevance score
                updateRelevanceScore(conn, url, relevanceScore);

                // Clear existing term associations for this page
                clearPageTerms(conn, pageId);

                // Insert terms and associations
                for (Map.Entry<String, Double> entry : tfidfVector.entrySet()) {
                    String term = entry.getKey();
                    double score = entry.getValue();

                    // Get or create term ID
                    long termId = getOrCreateTermId(conn, term);
                    if (termId < 0) {
                        continue;
                    }

                    // Insert page-term association
                    insertPageTerm(conn, pageId, termId, score);
                }
            });

            logger.trace("Saved index for URL: {} with {} terms", url, tfidfVector.size());

        } catch (SQLException e) {
            logger.error("Error saving index for URL: {}", url, e);
        }
    }

    /**
     * Search for documents matching query terms.
     *
     * @param queryTerms List of query terms
     * @param limit      Maximum results
     * @return List of search results
     */
    public List<ContentIndexer.SearchResult> search(List<String> queryTerms, int limit) {
        List<ContentIndexer.SearchResult> results = new ArrayList<>();

        if (queryTerms.isEmpty()) {
            return results;
        }

        // Build query to find pages containing any of the terms
        // and sum their TF-IDF scores
        StringBuilder sql = new StringBuilder();
        sql.append("""
            SELECT p.url, SUM(pt.tf_idf_score) as total_score
            FROM pages p
            JOIN page_terms pt ON p.id = pt.page_id
            JOIN index_terms it ON pt.term_id = it.id
            WHERE it.term IN (
            """);

        // Add placeholders for terms
        for (int i = 0; i < queryTerms.size(); i++) {
            if (i > 0) sql.append(", ");
            sql.append("?");
        }

        sql.append("""
            )
            GROUP BY p.id, p.url
            ORDER BY total_score DESC
            LIMIT ?
            """);

        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql.toString())) {
            int paramIndex = 1;
            for (String term : queryTerms) {
                ps.setString(paramIndex++, term);
            }
            ps.setInt(paramIndex, limit);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new ContentIndexer.SearchResult(
                            rs.getString("url"),
                            rs.getDouble("total_score")
                    ));
                }
            }
        } catch (SQLException e) {
            logger.error("Error searching index", e);
        }

        return results;
    }

    /**
     * Get terms for a page.
     *
     * @param url Page URL
     * @return List of terms with their scores
     */
    public List<TermScore> getTermsForPage(String url) {
        List<TermScore> terms = new ArrayList<>();

        String sql = """
            SELECT it.term, pt.tf_idf_score, pt.term_freq
            FROM page_terms pt
            JOIN index_terms it ON pt.term_id = it.id
            JOIN pages p ON pt.page_id = p.id
            WHERE p.url = ?
            ORDER BY pt.tf_idf_score DESC
            """;

        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, url);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    terms.add(new TermScore(
                            rs.getString("term"),
                            rs.getDouble("tf_idf_score"),
                            rs.getInt("term_freq")
                    ));
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting terms for page: {}", url, e);
        }

        return terms;
    }

    private long getOrCreatePageId(Connection conn, String url) throws SQLException {
        // First try to find existing page
        String selectSql = "SELECT id FROM pages WHERE url = ?";
        try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
            ps.setString(1, url);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("id");
                }
            }
        }

        // Page doesn't exist - shouldn't happen as pages are saved before indexing
        logger.error("BUG: Page not found for indexing (save page first): {}", url);
        return -1;
    }

    private long getOrCreateTermId(Connection conn, String term) throws SQLException {
        // Try to find existing term
        String selectSql = "SELECT id FROM index_terms WHERE term = ?";
        try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
            ps.setString(1, term);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    // Update document frequency
                    updateDocumentFrequency(conn, rs.getLong("id"));
                    return rs.getLong("id");
                }
            }
        }

        // Create new term
        String insertSql = "INSERT INTO index_terms (term, document_freq) VALUES (?, 1)";
        try (PreparedStatement ps = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, term);
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }

        return -1;
    }

    private void updateDocumentFrequency(Connection conn, long termId) throws SQLException {
        String sql = "UPDATE index_terms SET document_freq = document_freq + 1 WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, termId);
            ps.executeUpdate();
        }
    }

    private void clearPageTerms(Connection conn, long pageId) throws SQLException {
        String sql = "DELETE FROM page_terms WHERE page_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, pageId);
            ps.executeUpdate();
        }
    }

    private void insertPageTerm(Connection conn, long pageId, long termId, double score)
            throws SQLException {
        String sql = "INSERT INTO page_terms (page_id, term_id, tf_idf_score, term_freq) VALUES (?, ?, ?, 1)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, pageId);
            ps.setLong(2, termId);
            ps.setDouble(3, score);
            ps.executeUpdate();
        }
    }

    private void updateRelevanceScore(Connection conn, String url, double score) throws SQLException {
        String sql = "UPDATE pages SET relevance_score = ? WHERE url = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, score);
            ps.setString(2, url);
            ps.executeUpdate();
        }
    }

    /**
     * Term with score record.
     */
    public record TermScore(String term, double tfidfScore, int termFreq) {
    }
}
