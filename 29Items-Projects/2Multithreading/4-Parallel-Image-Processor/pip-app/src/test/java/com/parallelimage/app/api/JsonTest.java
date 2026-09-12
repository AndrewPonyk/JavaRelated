package com.parallelimage.app.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@link Json} tests, weighted towards the two ways a hand-rolled JSON layer actually breaks.
 *
 * <p>The first is <b>escaping on the way out</b>. A failure message derived from an exception contains a
 * newline more often than not, and an unescaped one produces a response that no parser accepts — so the
 * client reports a transport error instead of the image failure that was being described. The escaping
 * tests therefore go through {@link Json#parseFlatObject} rather than comparing strings, because a round
 * trip is the property that matters and a literal-text assertion passes for an escape that is merely
 * self-consistent.
 *
 * <p>The second is <b>silently mis-reading input</b>. This parser is allowed to refuse anything it does
 * not handle; what it must never do is accept a body and produce values that were not in it. The
 * rejection tests pin that: nesting, duplicate keys and trailing junk all raise
 * {@link Json.JsonException}, which the controller turns into a 400.
 */
class JsonTest {

    @Nested
    @DisplayName("writing")
    class Writing {

        @Test
        @DisplayName("scalars render by runtime type, not by declared type")
        void scalarTypes() {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("text", "hello");
            fields.put("int", 42);
            fields.put("long", 9_000_000_000L);
            fields.put("double", 1.5d);
            fields.put("bool", true);
            fields.put("nothing", null);
            assertEquals("{\"text\":\"hello\",\"int\":42,\"long\":9000000000,\"double\":1.5,"
                    + "\"bool\":true,\"nothing\":null}", Json.object(fields));
        }

        @Test
        @DisplayName("an unrecognised type becomes a quoted string, never an object graph")
        void unknownTypesAreQuoted() {
            // This fallback is why Path, enums and records need no special case here -- and why nothing
            // can accidentally serialise its internals.
            String rendered = Json.object(Map.of("path", Path.of("C:", "photos")));
            assertEquals(Path.of("C:", "photos").toString(),
                    Json.parseFlatObject(rendered).get("path"));
        }

        @Test
        @DisplayName("NaN and infinity become null, because neither is JSON")
        void nonFiniteNumbers() {
            // Reached by dividing a duration by a zero job count. Emitting bare NaN moves the parse error
            // into the client, which is the one place it cannot be diagnosed.
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("a", Double.NaN);
            fields.put("b", Double.POSITIVE_INFINITY);
            fields.put("c", Float.NEGATIVE_INFINITY);
            assertEquals("{\"a\":null,\"b\":null,\"c\":null}", Json.object(fields));
        }

        @Test
        @DisplayName("an empty map is an empty object, not an empty string")
        void emptyObject() {
            assertEquals("{}", Json.object(Map.of()));
            assertEquals("[]", Json.array(List.of()));
        }

        @Test
        @DisplayName("a list of scalars renders as an array")
        void lists() {
            assertEquals("{\"problems\":[\"a\",\"b\"]}", Json.object(Map.of("problems", List.of("a", "b"))));
        }

        @Test
        @DisplayName("one level of nesting renders as an object")
        void oneLevelOfNesting() {
            assertEquals("{\"outer\":{\"inner\":1}}",
                    Json.object(Map.of("outer", Map.of("inner", 1))));
        }

        @Test
        @DisplayName("control characters are escaped, so a stack-trace-derived message stays parseable")
        void controlCharactersAreEscaped() {
            String nasty = "line\none\ttab \"quoted\" back\\slash \u0001 \r\f\b end";
            String rendered = Json.object(Map.of("reason", nasty));
            assertEquals(nasty, Json.parseFlatObject(rendered).get("reason"), rendered);
            assertTrue(rendered.contains("\\u0001"), "0x01 must be escaped as \\uXXXX: " + rendered);
        }

        @Test
        @DisplayName("a key is escaped exactly like a value")
        void keysAreEscaped() {
            assertEquals("{\"a\\\"b\":1}", Json.object(Map.of("a\"b", 1)));
        }

        @Test
        @DisplayName("non-ASCII passes through unescaped, which UTF-8 responses allow")
        void nonAsciiIsNotEscaped() {
            // Deliberate: the controller sets charset=utf-8 and sizes Content-Length from the encoded
            // bytes, so \\u escaping would only make the body longer and harder to read.
            String rendered = Json.object(Map.of("path", "/photos/Ol\u00e9"));
            assertTrue(rendered.contains("Ol\u00e9"), rendered);
            assertEquals("/photos/Ol\u00e9", Json.parseFlatObject(rendered).get("path"));
        }
    }

    @Nested
    @DisplayName("parseFlatObject")
    class Reading {

        @Test
        @DisplayName("an absent or empty body is an empty map, not an error")
        void emptyBodies() {
            // A POST with no body is a client asking for the defaults. That is a valid request that
            // validation will then reject for a missing input directory -- with a message about the
            // directory, which is the useful one.
            assertEquals(Map.of(), Json.parseFlatObject(null));
            assertEquals(Map.of(), Json.parseFlatObject(""));
            assertEquals(Map.of(), Json.parseFlatObject("   \n "));
            assertEquals(Map.of(), Json.parseFlatObject("{}"));
            assertEquals(Map.of(), Json.parseFlatObject("{  }"));
        }

        @Test
        @DisplayName("every value comes back as its source text, unquoted")
        void valuesAreText() {
            Map<String, String> fields = Json.parseFlatObject("""
                    {"input": "/photos", "parallelism": 8, "quality": 0.82,
                     "recursive": true, "format": null}""");
            assertEquals("/photos", fields.get("input"));
            assertEquals("8", fields.get("parallelism"));
            assertEquals("0.82", fields.get("quality"));
            assertEquals("true", fields.get("recursive"));
            assertEquals("null", fields.get("format"));
        }

        @Test
        @DisplayName("whitespace anywhere structural is ignored")
        void whitespaceIsIgnored() {
            assertEquals(Map.of("a", "1", "b", "2"),
                    Json.parseFlatObject("  {\n\t\"a\"  :  1 ,\r\n \"b\" : 2\n}  "));
        }

        @Test
        @DisplayName("the escapes the writer produces are resolved by the reader")
        void escapesRoundTrip() {
            assertEquals("a\"b\\c/d\ne\tf\r\b\f\u0001",
                    Json.parseFlatObject(
                            "{\"k\":\"a\\\"b\\\\c\\/d\\ne\\tf\\r\\b\\f\\u0001\"}").get("k"));
        }

        @Test
        @DisplayName("nesting is refused with a message that says which kind")
        void nestingIsRefused() {
            assertTrue(assertThrows(Json.JsonException.class,
                    () -> Json.parseFlatObject("{\"a\":{\"b\":1}}")).getMessage().contains("objects"));
            assertTrue(assertThrows(Json.JsonException.class,
                    () -> Json.parseFlatObject("{\"a\":[1,2]}")).getMessage().contains("arrays"));
        }

        @Test
        @DisplayName("a duplicate key is refused rather than resolved last-wins")
        void duplicateKeysAreRefused() {
            // A duplicate means the client is building the body by string concatenation. Picking one of
            // the two values hides a real bug in it.
            Json.JsonException thrown = assertThrows(Json.JsonException.class,
                    () -> Json.parseFlatObject("{\"quality\":0.5,\"quality\":0.9}"));
            assertTrue(thrown.getMessage().contains("quality"), thrown.getMessage());
        }

        @ParameterizedTest
        @DisplayName("malformed input is refused, never partially accepted")
        @ValueSource(strings = {
            "[]",                       // an array, not an object
            "\"just a string\"",        // a bare scalar
            "{\"a\":1",                 // unterminated object
            "{\"a\"}",                  // no colon
            "{\"a\":}",                 // no value
            "{a:1}",                    // unquoted key
            "{\"a\":1,}",               // trailing comma
            "{\"a\":\"unterminated}",   // unterminated string
            "{\"a\":\"\\q\"}",          // unknown escape
            "{\"a\":\"\\uZZZZ\"}",      // bad hex escape
            "{\"a\":\"\\u12\"}",        // truncated escape
        })
        void malformedInputIsRefused(String body) {
            assertThrows(Json.JsonException.class, () -> Json.parseFlatObject(body), body);
        }

        @Test
        @DisplayName("an unquoted value must be a literal, so a missing comma cannot drop a field")
        void unquotedValuesMustBeLiterals() {
            // The scan for an unquoted value stops only at ',' and '}'. Without the literal check,
            // {"a":1 "b":2} parses as the single field a="1 \"b\":2" and b vanishes -- a field the client
            // sent and the endpoint ignored, which is what the unknown-field check exists to prevent.
            Json.JsonException thrown = assertThrows(Json.JsonException.class,
                    () -> Json.parseFlatObject("{\"a\":1 \"b\":2}"));
            assertTrue(thrown.getMessage().contains("not a JSON value"), thrown.getMessage());

            assertEquals(Map.of("a", "1", "b", "2"), Json.parseFlatObject("{\"a\":1,\"b\":2}"),
                    "the comma-separated form must still parse");
        }

        @ParameterizedTest
        @DisplayName("the three literals and any finite number are accepted unquoted")
        @ValueSource(strings = {"true", "false", "null", "0", "-1", "8", "0.82", "-0.5", "1e3", "1E-3"})
        void literalsAreAccepted(String literal) {
            assertEquals(Map.of("v", literal), Json.parseFlatObject("{\"v\":" + literal + "}"));
        }

        @ParameterizedTest
        @DisplayName("NaN and infinity are refused, matching what the writer emits for them")
        @ValueSource(strings = {"NaN", "Infinity", "-Infinity"})
        void nonFiniteLiteralsAreRefused(String literal) {
            // None of the three is JSON, but Double.parseDouble accepts all of them -- so without the
            // isFinite check a NaN quality would pass the validator's range test (every comparison against
            // NaN is false) and only fail in the core, as an exception the controller reads as a 500.
            assertThrows(Json.JsonException.class,
                    () -> Json.parseFlatObject("{\"quality\":" + literal + "}"), literal);
        }

        @Test
        @DisplayName("JsonException is an IllegalArgumentException, so it maps to 400 and never to 500")
        void exceptionType() {
            assertTrue(assertThrows(Json.JsonException.class, () -> Json.parseFlatObject("[]"))
                    instanceof IllegalArgumentException);
        }
    }

    @Nested
    @DisplayName("text")
    class TextAccessor {

        @Test
        @DisplayName("absent, blank and whitespace-only are all 'not supplied'")
        void blankIsAbsent() {
            // The distinction between a missing field and one sent as "" is not one any caller of this
            // API has a use for, and collapsing it here keeps every validation rule to a single branch.
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("blank", "");
            fields.put("spaces", "   ");
            assertEquals(Optional.empty(), Json.text(fields, "missing"));
            assertEquals(Optional.empty(), Json.text(fields, "blank"));
            assertEquals(Optional.empty(), Json.text(fields, "spaces"));
        }

        @Test
        @DisplayName("a supplied value is trimmed")
        void valuesAreTrimmed() {
            assertEquals(Optional.of("png"), Json.text(Map.of("format", "  png "), "format"));
        }
    }
}
