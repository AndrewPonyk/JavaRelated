package com.parallelimage.app.wiring;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.parallelimage.app.config.AppConfig;
import com.parallelimage.core.port.JobRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@link ServiceRegistry} tests: the full {@code open()} dispatch, degrading instead of refusing. */
class ServiceRegistryTest {

    @TempDir
    private Path root;

    private String asPropertyValue(Path path) {
        return path.toString().replace('\\', '/');
    }

    private AppConfig configFrom(String... lines) throws IOException {
        Path file = root.resolve("application-" + System.identityHashCode(lines) + ".properties");
        StringBuilder body = new StringBuilder();
        for (String line : lines) {
            body.append(line).append('\n');
        }
        Files.writeString(file, body.toString());
        return AppConfig.load(file);
    }

    @Nested
    @DisplayName("open()")
    class Open {

        @Test
        @DisplayName("db.enabled=false degrades to a no-op repository with history disabled")
        void databaseDisabledDegradesToNoOp() throws IOException {
            AppConfig config = configFrom("db.enabled=false");

            try (ServiceRegistry.Registry registry = ServiceRegistry.open(config)) {
                assertFalse(registry.historyEnabled());
                assertTrue(registry.repository() == JobRepository.NO_OP);
                assertNotNull(registry.engine());
                assertNotNull(registry.enhancerDescription());
            }
        }

        @Test
        @DisplayName("a real, writable database path opens a real repository with history enabled")
        void databaseEnabledOpensRealRepository() throws IOException {
            Path dbFile = root.resolve("history.db");
            AppConfig config = configFrom("db.enabled=true", "db.path=" + asPropertyValue(dbFile));

            try (ServiceRegistry.Registry registry = ServiceRegistry.open(config)) {
                assertTrue(registry.historyEnabled());
                assertFalse(registry.repository() == JobRepository.NO_OP);
            }

            // Re-opening the same, already-migrated file exercises the "schema up to date" branch.
            try (ServiceRegistry.Registry registry = ServiceRegistry.open(config)) {
                assertTrue(registry.historyEnabled());
            }
        }

        @Test
        @DisplayName("a database path that cannot be opened as SQLite degrades to a no-op repository")
        void unopenableDatabasePathDegradesToNoOp() throws IOException {
            Path dbAsDirectory = Files.createDirectories(root.resolve("not-a-file.db"));
            AppConfig config = configFrom("db.enabled=true", "db.path=" + asPropertyValue(dbAsDirectory));

            try (ServiceRegistry.Registry registry = ServiceRegistry.open(config)) {
                assertFalse(registry.historyEnabled());
                assertTrue(registry.repository() == JobRepository.NO_OP);
            }
        }

        @Test
        @DisplayName("retention.days > 0 runs a startup purge against a real repository")
        void retentionPurgeRunsWhenConfigured() throws IOException {
            Path dbFile = root.resolve("retained.db");
            AppConfig config = configFrom(
                    "db.enabled=true", "db.path=" + asPropertyValue(dbFile), "retention.days=30");

            try (ServiceRegistry.Registry registry = ServiceRegistry.open(config)) {
                assertTrue(registry.historyEnabled());
            }
        }
    }
}
