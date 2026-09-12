package com.example.pipeline.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.pipeline.domain.AggregateSnapshot;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The JDBC stub's contract: <em>every</em> operation throws, loudly, right away.
 *
 * <p>This test looks pointless until you know what it replaced. The class used to be
 * selected automatically whenever a JDBC URL was configured, so a run would generate,
 * filter and aggregate every event, write the CSV report, and only then throw from
 * {@link JdbcAggregateRepository#save} — reporting {@code FAILED} for work that had
 * succeeded. {@code PipelineApplication} now rejects a configured URL before any thread
 * starts. These assertions are what stop a future "let me just make save() a no-op so the
 * run goes green" change: a silent no-op would be a repository that claims to persist and
 * doesn't, which is strictly worse than one that throws.
 *
 * <p>{@link #noAccessorLeaksThePassword()} is a compile-time-shaped concern checked at
 * runtime on purpose — the risk is not that today's code leaks the password but that
 * tomorrow's {@code password()} getter or generated {@code toString} does, and a
 * reflective sweep catches both.
 */
@Timeout(10)
@DisplayName("JdbcAggregateRepository")
class JdbcAggregateRepositoryTest {

    private static final String URL = "jdbc:postgresql://localhost:5432/pipeline";

    private static JdbcAggregateRepository repository() {
        return new JdbcAggregateRepository(URL, "pipeline", "s3cr3t-not-a-real-password");
    }

    @Test
    @DisplayName("null constructor arguments are rejected")
    void nullsAreRejected() {
        assertThrows(NullPointerException.class,
                () -> new JdbcAggregateRepository(null, "u", "p"));
        assertThrows(NullPointerException.class,
                () -> new JdbcAggregateRepository(URL, null, "p"));
        assertThrows(NullPointerException.class,
                () -> new JdbcAggregateRepository(URL, "u", null));
    }

    @Test
    @DisplayName("save fails immediately and names the migration that defines the schema")
    void saveThrowsWithAnActionableMessage() {
        UnsupportedOperationException thrown = assertThrows(UnsupportedOperationException.class,
                () -> repository().save(AggregateSnapshot.empty()));
        // Pointing at the DDL turns "not implemented" into "here is what to implement against".
        assertTrue(thrown.getMessage().contains("V001__create_pipeline_tables.sql"), thrown.getMessage());
    }

    @Test
    @DisplayName("save validates its argument before failing, so a null is a null and not a stub message")
    void saveRejectsNullBeforeReportingItsOwnLimitation() {
        // Argument checks first: a caller passing null has a different bug from a caller
        // using an unfinished repository, and the exception type must say which.
        assertThrows(NullPointerException.class, () -> repository().save(null));
    }

    @Test
    @DisplayName("findLatest fails rather than pretending the database is empty")
    void findLatestThrows() {
        // Returning Optional.empty() here would be indistinguishable from "no run has been
        // persisted yet" - a lie that only shows up as missing history much later.
        assertThrows(UnsupportedOperationException.class, () -> repository().findLatest());
    }

    @Test
    @DisplayName("the configured URL and user are readable for diagnostics")
    void urlAndUserAreExposed() {
        JdbcAggregateRepository repository = repository();
        assertEquals(URL, repository.jdbcUrl());
        assertEquals("pipeline", repository.user());
    }

    @ParameterizedTest
    @DisplayName("hasPassword reports presence without revealing the value")
    @ValueSource(strings = {"", " ", "\t"})
    void blankPasswordsCountAsAbsent(String password) {
        // Blank rather than empty: a password read from a trimmed properties file arrives as
        // whitespace, and reporting that as "set" would send an operator hunting the wrong bug.
        assertFalse(new JdbcAggregateRepository(URL, "u", password).hasPassword());
    }

    @Test
    @DisplayName("a real password is reported as present")
    void nonBlankPasswordCountsAsPresent() {
        assertTrue(repository().hasPassword());
    }

    @Test
    @DisplayName("no accessor exposes the password, now or after a careless addition")
    void noAccessorLeaksThePassword() {
        String secret = "s3cr3t-not-a-real-password";
        JdbcAggregateRepository repository = new JdbcAggregateRepository(URL, "pipeline", secret);

        assertFalse(repository.toString().contains(secret),
                "toString is the most common accidental leak, straight into a log");

        for (Method method : JdbcAggregateRepository.class.getDeclaredMethods()) {
            if (method.getParameterCount() != 0 || !method.canAccess(repository)) {
                continue;
            }
            Object value = invokeQuietly(method, repository);
            if (value instanceof String text) {
                assertFalse(text.contains(secret),
                        method.getName() + "() returns the password; credentials must never leave this class");
            }
        }
    }

    private static Object invokeQuietly(Method method, JdbcAggregateRepository target) {
        try {
            return method.invoke(target);
        } catch (InvocationTargetException e) {
            // findLatest() throws by design, and a method that cannot return does not leak.
            return null;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("could not invoke " + method.getName(), e);
        }
    }
}
