package com.parallelimage.persistence.migration;

import com.parallelimage.persistence.PersistenceException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Splits a {@code .sql} file into individual statements for {@link java.sql.Statement#execute}.
 *
 * <h2>Why hand-rolled</h2>
 * JDBC has no "execute this script" call, and sqlite-jdbc will not accept several statements in one
 * {@code execute}. So something has to split on semicolons, and splitting on semicolons naively is
 * wrong the moment a string literal contains one — {@code V002} seeds preset names, and a preset
 * called {@code "Proof; draft"} would be cut in half. This is not a SQL parser; it is a lexer that
 * knows the four contexts in which a semicolon is not a terminator:
 *
 * <ul>
 *   <li>inside a {@code 'single-quoted string'}, where {@code ''} is an escaped quote;</li>
 *   <li>inside a {@code "quoted identifier"} (and its SQLite cousins, {@code [brackets]} and
 *       {@code `backticks`});</li>
 *   <li>after {@code --} to the end of the line;</li>
 *   <li>inside a {@code /* block comment *}{@code /}.</li>
 * </ul>
 *
 * <h2>The limitation, stated rather than discovered</h2>
 * A {@code CREATE TRIGGER} body is {@code BEGIN ... END} with semicolons between its inner statements,
 * and no amount of lexing tells you that those semicolons are not terminators without tracking
 * {@code BEGIN}/{@code END} nesting — which is ambiguous, because {@code BEGIN} is also how you start
 * a transaction. Rather than half-implement that, {@link #split} rejects a script containing
 * {@code CREATE TRIGGER} with an explanation. No migration needs one today; if one does, it belongs in
 * a Java migration step, not in a smarter splitter.
 */
public final class SqlScript {

    private SqlScript() {
    }

    /**
     * Splits a migration script into executable statements.
     *
     * @param script the whole file, line endings already normalised to {@code \n}
     * @return the statements in order, trimmed, with comment-only fragments dropped
     * @throws PersistenceException if the script uses {@code CREATE TRIGGER} (see the class javadoc)
     */
    // The complexity metrics are suppressed here and nowhere else in the codebase. A lexer's dispatch
    // loop is one decision per token class, and the four contexts this one recognises are listed in
    // the class javadoc — the branch count is the specification, so lowering it would mean either
    // handling fewer contexts (wrong) or scattering the loop across helpers that can only be read in
    // the order the loop already puts them in. NPath is 2017 because it multiplies the independent
    // `i + 1 < length` guards, none of which interact. The project-wide thresholds in
    // config/pmd/ruleset.xml stay where they are so they still catch a method that grew a second
    // responsibility, which is what they are for.
    @SuppressWarnings({"PMD.CyclomaticComplexity", "PMD.NPathComplexity"})
    public static List<String> split(String script) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder(256);

        int length = script.length();
        int i = 0;
        while (i < length) {
            char c = script.charAt(i);

            // -- line comment: drop it, but keep the newline so tokens do not fuse together.
            if (c == '-' && i + 1 < length && script.charAt(i + 1) == '-') {
                while (i < length && script.charAt(i) != '\n') {
                    i++;
                }
                current.append('\n');
                continue;
            }

            // /* block comment */ — replaced by a space for the same reason.
            if (c == '/' && i + 1 < length && script.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < length && !(script.charAt(i) == '*' && script.charAt(i + 1) == '/')) {
                    i++;
                }
                i = Math.min(i + 2, length);
                current.append(' ');
                continue;
            }

            if (c == '\'') {
                i = copyQuoted(script, i, '\'', current);
                continue;
            }
            if (c == '"') {
                i = copyQuoted(script, i, '"', current);
                continue;
            }
            if (c == '`') {
                i = copyQuoted(script, i, '`', current);
                continue;
            }
            if (c == '[') {
                // SQLite's MS-Access-compatible identifier quoting: no doubling rule, ends at ']'.
                int end = script.indexOf(']', i + 1);
                if (end < 0) {
                    throw new PersistenceException("unterminated [identifier] in migration script");
                }
                current.append(script, i, end + 1);
                i = end + 1;
                continue;
            }

            if (c == ';') {
                addIfNotBlank(statements, current);
                current.setLength(0);
                i++;
                continue;
            }

            current.append(c);
            i++;
        }

        // A trailing statement without a terminating semicolon is accepted rather than rejected: it
        // is legal SQL to a human reader, and refusing it would be a trap for whoever writes V004.
        addIfNotBlank(statements, current);
        return statements;
    }

    /**
     * Copies a quoted run starting at {@code start}, including both delimiters, and returns the index
     * just past it. A doubled delimiter is an escaped delimiter and does not end the run.
     */
    private static int copyQuoted(String script, int start, char quote, StringBuilder out) {
        int length = script.length();
        out.append(quote);
        int i = start + 1;
        while (i < length) {
            char c = script.charAt(i);
            if (c == quote) {
                if (i + 1 < length && script.charAt(i + 1) == quote) {
                    out.append(quote).append(quote);
                    i += 2;
                    continue;
                }
                out.append(quote);
                return i + 1;
            }
            out.append(c);
            i++;
        }
        throw new PersistenceException(
                "unterminated " + quote + "-quoted literal in migration script");
    }

    private static void addIfNotBlank(List<String> statements, StringBuilder current) {
        String statement = current.toString().trim();
        if (statement.isEmpty()) {
            return;
        }
        if (statement.toUpperCase(Locale.ROOT).contains("CREATE TRIGGER")) {
            throw new PersistenceException("CREATE TRIGGER is not supported by the migration"
                    + " splitter: its BEGIN...END body contains semicolons that cannot be told apart"
                    + " from statement terminators. Express the logic in the adapter instead.");
        }
        statements.add(statement);
    }
}
