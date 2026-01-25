package com.crawler.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.Optional;

/**
 * Repository for saving and loading crawl state for resumption.
 */
public class CrawlStateRepository {

    private static final Logger logger = LoggerFactory.getLogger(CrawlStateRepository.class);

    private final DatabaseManager dbManager;

    public CrawlStateRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    /**
     * Save current crawl state for later resumption.
     *
     * @param frontierSnapshot JSON snapshot of URL frontier
     * @param visitedSnapshot  JSON snapshot of visited URLs
     * @return State ID
     */
    public long saveState(String frontierSnapshot, String visitedSnapshot) {
        String sql = """
            INSERT INTO crawl_state (frontier_snapshot, visited_snapshot, status)
            VALUES (?, ?, 'active')
            """;

        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql,
                Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, frontierSnapshot);
            ps.setString(2, visitedSnapshot);
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    long id = rs.getLong(1);
                    logger.info("Saved crawl state: id={}", id);
                    return id;
                }
            }
        } catch (SQLException e) {
            logger.error("Error saving crawl state", e);
        }

        return -1;
    }

    /**
     * Load the most recent active crawl state.
     *
     * @return Optional containing state if found
     */
    public Optional<CrawlState> loadLatestState() {
        String sql = """
            SELECT * FROM crawl_state
            WHERE status = 'active'
            ORDER BY saved_at DESC
            LIMIT 1
            """;

        try (Statement stmt = dbManager.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                return Optional.of(new CrawlState(
                        rs.getLong("id"),
                        rs.getString("frontier_snapshot"),
                        rs.getString("visited_snapshot"),
                        rs.getTimestamp("saved_at"),
                        rs.getString("status")
                ));
            }
        } catch (SQLException e) {
            logger.error("Error loading crawl state", e);
        }

        return Optional.empty();
    }

    /**
     * Mark a crawl state as completed.
     *
     * @param stateId State ID to mark
     */
    public void markCompleted(long stateId) {
        String sql = "UPDATE crawl_state SET status = 'completed' WHERE id = ?";

        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setLong(1, stateId);
            ps.executeUpdate();
            logger.info("Marked crawl state {} as completed", stateId);
        } catch (SQLException e) {
            logger.error("Error marking crawl state as completed: {}", stateId, e);
        }
    }

    /**
     * Delete old crawl states.
     *
     * @param keepRecent Number of recent states to keep
     */
    public void cleanup(int keepRecent) {
        String sql = """
            DELETE FROM crawl_state
            WHERE id NOT IN (
                SELECT id FROM crawl_state
                ORDER BY saved_at DESC
                LIMIT ?
            )
            """;

        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setInt(1, keepRecent);
            int deleted = ps.executeUpdate();
            if (deleted > 0) {
                logger.info("Cleaned up {} old crawl states", deleted);
            }
        } catch (SQLException e) {
            logger.error("Error cleaning up crawl states", e);
        }
    }

    /**
     * Crawl state record.
     */
    public record CrawlState(
            long id,
            String frontierSnapshot,
            String visitedSnapshot,
            Timestamp savedAt,
            String status
    ) {
    }
}
