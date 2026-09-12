package com.parallelimage.persistence.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.parallelimage.persistence.PersistenceException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link SqlScript} tests.
 *
 * <p>These are cheap and there are a lot of them because the splitter is the one place where a bug
 * would corrupt a migration <em>silently</em>: a statement cut in the wrong place either fails with a
 * baffling syntax error or, worse, executes a truncated-but-valid statement. Every case here is a real
 * construct from {@code /migrations}, plus the ones a V004 is likely to introduce.
 */
class SqlScriptTest {

    @Test
    @DisplayName("plain statements split on semicolons and lose the terminator")
    void splitsOnSemicolons() {
        List<String> statements = SqlScript.split("SELECT 1; SELECT 2;");

        assertEquals(List.of("SELECT 1", "SELECT 2"), statements);
    }

    @Test
    @DisplayName("a trailing statement without a semicolon is kept")
    void keepsUnterminatedTail() {
        assertEquals(List.of("SELECT 1", "SELECT 2"), SqlScript.split("SELECT 1;\nSELECT 2"));
    }

    @Test
    @DisplayName("a semicolon inside a string literal is not a terminator")
    void ignoresSemicolonsInStrings() {
        // V002 seeds preset names. A name like 'Proof; draft' must not be cut in half.
        List<String> statements =
                SqlScript.split("INSERT INTO t (name) VALUES ('Proof; draft'); SELECT 1;");

        assertEquals(2, statements.size(), statements.toString());
        assertTrue(statements.get(0).contains("'Proof; draft'"), statements.get(0));
    }

    @Test
    @DisplayName("a doubled quote escapes a quote and does not end the literal")
    void handlesEscapedQuotes() {
        List<String> statements = SqlScript.split("INSERT INTO t VALUES ('it''s; fine'); SELECT 2;");

        assertEquals(2, statements.size(), statements.toString());
        assertTrue(statements.get(0).contains("'it''s; fine'"), statements.get(0));
    }

    @Test
    @DisplayName("a semicolon inside a quoted identifier is not a terminator")
    void ignoresSemicolonsInIdentifiers() {
        assertEquals(1, SqlScript.split("SELECT \"odd;name\" FROM t;").size());
        assertEquals(1, SqlScript.split("SELECT [odd;name] FROM t;").size());
        assertEquals(1, SqlScript.split("SELECT `odd;name` FROM t;").size());
    }

    @Test
    @DisplayName("line comments are stripped, including ones holding a semicolon")
    void stripsLineComments() {
        String script = """
                -- a comment; with a semicolon
                SELECT 1; -- trailing comment
                SELECT 2;
                """;

        assertEquals(List.of("SELECT 1", "SELECT 2"), SqlScript.split(script));
    }

    @Test
    @DisplayName("a comment-only script yields no statements")
    void commentOnlyScriptIsEmpty() {
        assertEquals(List.of(), SqlScript.split("-- nothing here\n-- or here\n"));
    }

    @Test
    @DisplayName("block comments are stripped and do not fuse the tokens around them")
    void stripsBlockComments() {
        List<String> statements = SqlScript.split("SELECT/* inline; comment */1; SELECT 2;");

        assertEquals(2, statements.size(), statements.toString());
        assertTrue(statements.get(0).matches("SELECT\\s+1"), statements.get(0));
    }

    @Test
    @DisplayName("a division is not the start of a block comment")
    void divisionIsNotAComment() {
        // v_recent_jobs computes pixels / 1000.0 / duration_ms. A naive '/' check would eat the rest.
        List<String> statements = SqlScript.split("SELECT a / 1000.0 / b FROM t;");

        assertEquals(1, statements.size());
        assertTrue(statements.get(0).contains("/ 1000.0 /"), statements.get(0));
    }

    @Test
    @DisplayName("a lone hyphen is not the start of a comment")
    void singleHyphenIsNotAComment() {
        List<String> statements = SqlScript.split("SELECT a - b FROM t;");

        assertEquals(1, statements.size());
        assertTrue(statements.get(0).contains("a - b"), statements.get(0));
    }

    @Test
    @DisplayName("blank fragments between semicolons are dropped")
    void dropsBlankFragments() {
        assertEquals(List.of("SELECT 1"), SqlScript.split(";;\nSELECT 1;;\n"));
    }

    @Test
    @DisplayName("CREATE TRIGGER is rejected with an explanation rather than mis-split")
    void rejectsTriggers() {
        String script = """
                CREATE TRIGGER t AFTER INSERT ON jobs BEGIN
                    UPDATE batches SET total = total + 1;
                END;
                """;

        PersistenceException thrown =
                assertThrows(PersistenceException.class, () -> SqlScript.split(script));
        assertTrue(thrown.getMessage().contains("CREATE TRIGGER"), thrown.getMessage());
    }

    @Test
    @DisplayName("an unterminated literal fails loudly instead of silently swallowing the rest")
    void rejectsUnterminatedLiteral() {
        assertThrows(PersistenceException.class, () -> SqlScript.split("SELECT 'oops;"));
        assertThrows(PersistenceException.class, () -> SqlScript.split("SELECT [oops;"));
    }
}
