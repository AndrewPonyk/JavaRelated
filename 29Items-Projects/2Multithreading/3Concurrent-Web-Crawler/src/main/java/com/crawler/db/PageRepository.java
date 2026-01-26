package com.crawler.db;

import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Repository for CRUD operations on crawled pages.
 */
public class PageRepository {

    private static final Logger logger = LoggerFactory.getLogger(PageRepository.class);

    private final DatabaseManager dbManager;

    public PageRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    /**
     * Save a crawled page to the database.
     *
     * @param url        Page URL
     * @param document   Parsed HTML document
     * @param statusCode HTTP status code
     * @return Generated page ID
     */
    public long save(String url, Document document, int statusCode) {
        String sql = """
            INSERT INTO pages (url, domain, title, content_hash, content_length, status_code, crawled_at)
            VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT(url) DO UPDATE SET
                title = excluded.title,
                content_hash = excluded.content_hash,
                content_length = excluded.content_length,
                status_code = excluded.status_code,
                crawled_at = CURRENT_TIMESTAMP
            """;

        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql,
                Statement.RETURN_GENERATED_KEYS)) {

            String domain = extractDomain(url);
            String title = document != null ? document.title() : null;
            String text = document != null ? document.text() : "";
            String contentHash = document != null ? calculateHash(text) : null;
            int contentLength = text.length();

            ps.setString(1, url);
            ps.setString(2, domain);
            ps.setString(3, title);
            ps.setString(4, contentHash);
            ps.setInt(5, contentLength);
            ps.setInt(6, statusCode);

            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    long id = rs.getLong(1);
                    logger.trace("Saved page: id={}, url={}", id, url);
                    return id;
                }
            }

            return -1;

        } catch (SQLException e) {
            logger.error("Error saving page: {}", url, e);
            return -1;
        }
    }

    /**
     * Update the relevance score for a page.
     *
     * @param url   Page URL
     * @param score Relevance score (0.0 to 1.0)
     */
    public void updateRelevanceScore(String url, double score) {
        String sql = "UPDATE pages SET relevance_score = ? WHERE url = ?";

        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setDouble(1, score);
            ps.setString(2, url);
            int updated = ps.executeUpdate();
            if (updated == 0) {
                logger.warn("No rows updated for URL (not found?): {}", url);
            } else {
                logger.trace("Updated relevance_score={} for: {}", score, url);
            }
        } catch (SQLException e) {
            logger.error("Error updating relevance score for: {}", url, e);
        }
    }

    /**
     * Find a page by URL.
     *
     * @param url Page URL
     * @return Optional containing page data if found
     */
    public Optional<PageData> findByUrl(String url) {
        String sql = "SELECT * FROM pages WHERE url = ?";

        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, url);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSet(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding page: {}", url, e);
        }

        return Optional.empty();
    }

    /**
     * Find pages by domain.
     *
     * @param domain Domain name
     * @param limit  Maximum number of results
     * @return List of pages for the domain
     */
    public List<PageData> findByDomain(String domain, int limit) {
        String sql = "SELECT * FROM pages WHERE domain = ? ORDER BY crawled_at DESC LIMIT ?";
        List<PageData> pages = new ArrayList<>();

        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, domain);
            ps.setInt(2, limit);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    pages.add(mapResultSet(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding pages for domain: {}", domain, e);
        }

        return pages;
    }

    /**
     * Find top pages by relevance score.
     *
     * @param limit Maximum number of results
     * @return List of pages sorted by relevance
     */
    public List<PageData> findTopByRelevance(int limit) {
        String sql = "SELECT * FROM pages ORDER BY relevance_score DESC LIMIT ?";
        List<PageData> pages = new ArrayList<>();

        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setInt(1, limit);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    pages.add(mapResultSet(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding top pages", e);
        }

        return pages;
    }

    /**
     * Check if a URL has been crawled.
     *
     * @param url URL to check
     * @return true if already crawled
     */
    public boolean exists(String url) {
        String sql = "SELECT 1 FROM pages WHERE url = ?";

        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, url);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            logger.error("Error checking page existence: {}", url, e);
            return false;
        }
    }

    /**
     * Get total count of crawled pages.
     */
    public long count() {
        String sql = "SELECT COUNT(*) FROM pages";

        try (Statement stmt = dbManager.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            logger.error("Error counting pages", e);
        }

        return 0;
    }

    /**
     * Delete a page by URL.
     */
    public boolean delete(String url) {
        String sql = "DELETE FROM pages WHERE url = ?";

        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, url);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Error deleting page: {}", url, e);
            return false;
        }
    }

    private PageData mapResultSet(ResultSet rs) throws SQLException {
        return new PageData(
                rs.getLong("id"),
                rs.getString("url"),
                rs.getString("domain"),
                rs.getString("title"),
                rs.getString("content_hash"),
                rs.getInt("content_length"),
                rs.getInt("status_code"),
                rs.getDouble("relevance_score"),
                rs.getTimestamp("crawled_at"),
                rs.getTimestamp("last_modified")
        );
    }

    private String extractDomain(String url) {
        try {
            return new URL(url).getHost();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String calculateHash(String content) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(content.getBytes());
            return java.util.HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Data class for page records.
     */
    public record PageData(
            long id,
            String url,
            String domain,
            String title,
            String contentHash,
            int contentLength,
            int statusCode,
            double relevanceScore,
            Timestamp crawledAt,
            Timestamp lastModified
    ) {
    }
}
