package com.crawler.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.*;

/**
 * Manages SQLite database connections and initialization.
 *
 * <p>Features:
 * <ul>
 *   <li>Connection management</li>
 *   <li>Schema initialization</li>
 *   <li>WAL mode for better concurrency</li>
 *   <li>Graceful shutdown</li>
 * </ul>
 */
public class DatabaseManager { // |su:83 SQLite persistence - manages connection, schema, transactions

    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);

    private final String databasePath;
    private Connection connection; // |su:84 Single connection - SQLite handles concurrency via WAL mode

    public DatabaseManager(String databasePath) {
        this.databasePath = databasePath;
    }

    /**
     * Initialize the database connection and schema.
     */
    public void initialize() throws SQLException {
        // Ensure directory exists
        File dbFile = new File(databasePath);
        File parentDir = dbFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        // Connect to SQLite
        String jdbcUrl = "jdbc:sqlite:" + databasePath;
        connection = DriverManager.getConnection(jdbcUrl);

        // |su:85 WAL mode: Write-Ahead Logging - allows concurrent reads while writing
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL"); // |su:86 WAL: readers don't block writers
            stmt.execute("PRAGMA synchronous=NORMAL"); // |su:87 NORMAL: balance between safety & speed
            stmt.execute("PRAGMA foreign_keys=ON");
        }

        // Initialize schema
        createSchema();

        logger.info("Database initialized at: {}", databasePath);
    }

    /**
     * Create database schema if not exists.
     */
    private void createSchema() throws SQLException {
        String schema = """
            -- Crawled pages
            CREATE TABLE IF NOT EXISTS pages (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                url TEXT UNIQUE NOT NULL,
                domain TEXT NOT NULL,
                title TEXT,
                content_hash TEXT,
                status_code INTEGER,
                relevance_score REAL DEFAULT 0.0,
                crawled_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                last_modified TIMESTAMP
            );

            -- Index for URL lookups
            CREATE INDEX IF NOT EXISTS idx_pages_url ON pages(url);

            -- Index for domain filtering
            CREATE INDEX IF NOT EXISTS idx_pages_domain ON pages(domain);

            -- Index terms for TF-IDF
            CREATE TABLE IF NOT EXISTS index_terms (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                term TEXT UNIQUE NOT NULL,
                document_freq INTEGER DEFAULT 1
            );

            -- Index for term lookups
            CREATE INDEX IF NOT EXISTS idx_terms_term ON index_terms(term);

            -- Page-term relationships (TF-IDF scores)
            CREATE TABLE IF NOT EXISTS page_terms (
                page_id INTEGER NOT NULL,
                term_id INTEGER NOT NULL,
                tf_idf_score REAL NOT NULL,
                term_freq INTEGER NOT NULL,
                PRIMARY KEY (page_id, term_id),
                FOREIGN KEY (page_id) REFERENCES pages(id) ON DELETE CASCADE,
                FOREIGN KEY (term_id) REFERENCES index_terms(id) ON DELETE CASCADE
            );

            -- Crawl state for resumption
            CREATE TABLE IF NOT EXISTS crawl_state (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                frontier_snapshot TEXT,
                visited_snapshot TEXT,
                saved_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                status TEXT DEFAULT 'active'
            );

            -- Robots.txt cache
            CREATE TABLE IF NOT EXISTS robots_cache (
                domain TEXT PRIMARY KEY,
                rules TEXT,
                crawl_delay REAL DEFAULT 0.0,
                fetched_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                expires_at TIMESTAMP
            );
            """;

        try (Statement stmt = connection.createStatement()) {
            for (String sql : schema.split(";")) {
                sql = sql.trim();
                if (!sql.isEmpty()) {
                    stmt.execute(sql);
                }
            }
        }

        logger.debug("Database schema created/verified");
    }

    /**
     * Get the database connection.
     * Note: In production, consider using a connection pool.
     */
    public Connection getConnection() {
        return connection;
    }

    /**
     * Execute a transaction with automatic commit/rollback.
     *
     * @param action The action to execute within the transaction
     */
    public void executeInTransaction(TransactionAction action) throws SQLException { // |su:88 Transaction wrapper with auto-rollback on error
        boolean autoCommit = connection.getAutoCommit();
        try {
            connection.setAutoCommit(false); // |su:89 Disable autocommit = start transaction
            action.execute(connection);
            connection.commit(); // |su:90 Commit: make all changes permanent
        } catch (SQLException e) {
            connection.rollback(); // |su:91 Rollback: undo all changes on error (ACID)
            throw e;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    /**
     * Close the database connection.
     */
    public void close() {
        if (connection != null) {
            try {
                connection.close();
                logger.info("Database connection closed");
            } catch (SQLException e) {
                logger.error("Error closing database connection", e);
            }
        }
    }

    /**
     * Check if the database is connected.
     */
    public boolean isConnected() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Functional interface for transaction actions.
     */
    @FunctionalInterface
    public interface TransactionAction {
        void execute(Connection conn) throws SQLException;
    }
}
