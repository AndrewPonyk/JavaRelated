package com.crawler.db;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DatabaseManager.
 */
class DatabaseManagerTest {

    @TempDir
    Path tempDir;

    private DatabaseManager dbManager;

    @BeforeEach
    void setUp() throws SQLException {
        String dbPath = tempDir.resolve("test.db").toString();
        dbManager = new DatabaseManager(dbPath);
        dbManager.initialize();
    }

    @AfterEach
    void tearDown() {
        if (dbManager != null) {
            dbManager.close();
        }
    }

    @Test
    @DisplayName("Should initialize database and create schema")
    void shouldInitializeDatabase() {
        assertTrue(dbManager.isConnected());
    }

    @Test
    @DisplayName("Should create pages table")
    void shouldCreatePagesTable() throws SQLException {
        Connection conn = dbManager.getConnection();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT name FROM sqlite_master WHERE type='table' AND name='pages'"
             )) {
            assertTrue(rs.next());
        }
    }

    @Test
    @DisplayName("Should create index_terms table")
    void shouldCreateIndexTermsTable() throws SQLException {
        Connection conn = dbManager.getConnection();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT name FROM sqlite_master WHERE type='table' AND name='index_terms'"
             )) {
            assertTrue(rs.next());
        }
    }

    @Test
    @DisplayName("Should create page_terms table")
    void shouldCreatePageTermsTable() throws SQLException {
        Connection conn = dbManager.getConnection();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT name FROM sqlite_master WHERE type='table' AND name='page_terms'"
             )) {
            assertTrue(rs.next());
        }
    }

    @Test
    @DisplayName("Should create crawl_state table")
    void shouldCreateCrawlStateTable() throws SQLException {
        Connection conn = dbManager.getConnection();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT name FROM sqlite_master WHERE type='table' AND name='crawl_state'"
             )) {
            assertTrue(rs.next());
        }
    }

    @Test
    @DisplayName("Should create robots_cache table")
    void shouldCreateRobotsCacheTable() throws SQLException {
        Connection conn = dbManager.getConnection();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT name FROM sqlite_master WHERE type='table' AND name='robots_cache'"
             )) {
            assertTrue(rs.next());
        }
    }

    @Test
    @DisplayName("Should execute transaction successfully")
    void shouldExecuteTransactionSuccessfully() throws SQLException {
        dbManager.executeInTransaction(conn -> {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("INSERT INTO pages (url, domain, status_code) VALUES ('https://test.com', 'test.com', 200)");
            }
        });

        Connection conn = dbManager.getConnection();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM pages")) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1));
        }
    }

    @Test
    @DisplayName("Should rollback transaction on error")
    void shouldRollbackTransactionOnError() {
        try {
            dbManager.executeInTransaction(conn -> {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("INSERT INTO pages (url, domain, status_code) VALUES ('https://test.com', 'test.com', 200)");
                    // This will fail due to duplicate URL
                    stmt.execute("INSERT INTO pages (url, domain, status_code) VALUES ('https://test.com', 'test.com', 200)");
                }
            });
            fail("Expected SQLException");
        } catch (SQLException e) {
            // Expected
        }

        // Transaction should have rolled back
        try {
            Connection conn = dbManager.getConnection();
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM pages")) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1));
            }
        } catch (SQLException e) {
            fail("Failed to verify rollback: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Should close connection properly")
    void shouldCloseConnectionProperly() {
        assertTrue(dbManager.isConnected());
        dbManager.close();
        assertFalse(dbManager.isConnected());
    }

    @Test
    @DisplayName("Should enable WAL mode")
    void shouldEnableWalMode() throws SQLException {
        Connection conn = dbManager.getConnection();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA journal_mode")) {
            assertTrue(rs.next());
            assertEquals("wal", rs.getString(1).toLowerCase());
        }
    }

    @Test
    @DisplayName("Should create parent directory if not exists")
    void shouldCreateParentDirectory() throws SQLException {
        String deepPath = tempDir.resolve("a/b/c/test.db").toString();
        DatabaseManager deepDbManager = new DatabaseManager(deepPath);
        deepDbManager.initialize();

        assertTrue(deepDbManager.isConnected());
        deepDbManager.close();
    }
}
