package com.example.pipeline.infrastructure.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Contract of the hand-rolled JSON writer and flat reader.
 *
 * <p>Two different standards are applied on purpose, matching what the class documents. The
 * <strong>writer</strong> faces a browser, so its escaping is tested as a correctness and
 * injection property: a quote, a backslash or a control character in a sensor id must not be
 * able to break out of its string literal. The <strong>reader</strong> is deliberately
 * partial, so the tests pin its stated limits — nesting, arrays and escape sequences are
 * rejected rather than half-parsed — which is what stops the next person from mistaking it
 * for a JSON library and feeding it a real payload.
 */
@DisplayName("Json")
class JsonTest {

    @Nested
    @DisplayName("writer: value()")
    class Values {

        @Test
        @DisplayName("null renders as the JSON null literal")
        void nullIsLiteralNull() {
            assertEquals("null", Json.value(null));
        }

        @ParameterizedTest
        @DisplayName("booleans render bare")
        @CsvSource({"true, true", "false, false"})
        void booleansAreBare(boolean input, String expected) {
            assertEquals(expected, Json.value(input));
        }

        @Test
        @DisplayName("integral numbers render bare and exactly")
        void integralNumbersAreBare() {
            assertEquals("42", Json.value(42));
            assertEquals("-7", Json.value(-7L));
            assertEquals("9223372036854775807", Json.value(Long.MAX_VALUE));
            assertEquals("0", Json.value(0));
        }

        @Test
        @DisplayName("doubles render with six decimal places, in the root locale")
        void doublesUseFixedPrecision() {
            // Locale.ROOT matters: a comma decimal separator under a German locale would
            // emit "1,5", which is two JSON values, not one.
            assertEquals("1.500000", Json.value(1.5d));
            assertEquals("-0.250000", Json.value(-0.25d));
            assertEquals("100.000000", Json.value(100.0d));
        }

        @Test
        @DisplayName("floats take the same path as doubles")
        void floatsAreFormattedLikeDoubles() {
            assertEquals("2.500000", Json.value(2.5f));
        }

        /**
         * JSON has no {@code NaN} or {@code Infinity} literal. An average over an empty
         * sensor window is exactly how one of these reaches the writer, and emitting it
         * would produce a body that no strict client parser accepts.
         */
        @ParameterizedTest
        @DisplayName("non-finite doubles render as null, not as NaN or Infinity")
        @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
        void nonFiniteDoublesBecomeNull(double value) {
            assertEquals("null", Json.value(value));
        }

        @Test
        @DisplayName("anything else renders as a quoted string")
        void otherValuesAreQuotedStrings() {
            assertEquals("\"up\"", Json.value("up"));
            assertEquals("\"\"", Json.value(""));
        }
    }

    @Nested
    @DisplayName("writer: escaping")
    class Escaping {

        @Test
        @DisplayName("a quote in a value cannot terminate its string literal")
        void quotesAreEscaped() {
            assertEquals("\"say \\\"hi\\\"\"", Json.value("say \"hi\""));
        }

        @Test
        @DisplayName("a backslash is escaped, so a trailing one cannot escape the closing quote")
        void backslashesAreEscaped() {
            assertEquals("\"C:\\\\temp\"", Json.value("C:\\temp"));
            // The nasty case: an unescaped trailing backslash would turn the closing quote
            // into a literal and swallow the rest of the document.
            assertEquals("\"end\\\\\"", Json.value("end\\"));
        }

        @Test
        @DisplayName("newline, carriage return and tab use their short escapes")
        void whitespaceControlCharactersUseShortEscapes() {
            assertEquals("\"a\\nb\"", Json.value("a\nb"));
            assertEquals("\"a\\rb\"", Json.value("a\rb"));
            assertEquals("\"a\\tb\"", Json.value("a\tb"));
        }

        @Test
        @DisplayName("other control characters use the \\u escape")
        void otherControlCharactersUseUnicodeEscapes() {
            assertEquals("\"\\u0000\"", Json.value("\u0000"));
            assertEquals("\"\\u001f\"", Json.value("\u001f"));
            assertEquals("\"a\\u0007b\"", Json.value("a\u0007b"));
        }

        @Test
        @DisplayName("a space is not a control character and stays literal")
        void spaceIsNotEscaped() {
            assertEquals("\"a b\"", Json.value("a b"));
        }

        @Test
        @DisplayName("non-ASCII text is passed through, since the response is UTF-8")
        void nonAsciiIsPassedThrough() {
            // The handler declares charset=utf-8, so escaping these would be noise.
            assertEquals("\"caf\u00e9\"", Json.value("caf\u00e9"));
        }

        @Test
        @DisplayName("keys are escaped too, not just values")
        void keysAreEscaped() {
            assertEquals("{\"a\\\"b\":1}", Json.object(Map.of("a\"b", 1)));
        }
    }

    @Nested
    @DisplayName("writer: object()")
    class Objects {

        @Test
        @DisplayName("an empty map renders as an empty object")
        void emptyMapIsEmptyObject() {
            assertEquals("{}", Json.object(Map.of()));
        }

        @Test
        @DisplayName("fields keep the map's iteration order and are comma-separated")
        void insertionOrderIsPreserved() {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("status", "up");
            fields.put("queue.raw.depth", 3);
            fields.put("threshold", 50.0d);
            assertEquals("{\"status\":\"up\",\"queue.raw.depth\":3,\"threshold\":50.000000}",
                    Json.object(fields));
        }

        @Test
        @DisplayName("a null value renders as null rather than throwing")
        void nullValuesAreTolerated() {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("absent", null);
            assertEquals("{\"absent\":null}", Json.object(fields));
        }

        @Test
        @DisplayName("a null map is rejected")
        void nullMapIsRejected() {
            assertThrows(NullPointerException.class, () -> Json.object(null));
        }

        /**
         * The writer's output must survive a round trip through the reader for the flat
         * shapes the control plane actually emits — otherwise a client written against
         * {@code /health} could not read what {@code /health} produces.
         */
        @Test
        @DisplayName("a flat object written here can be read back by the reader")
        void writtenObjectIsReadable() {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("status", "up");
            fields.put("count", 7);
            Map<String, String> parsed = Json.parseFlatObject(Json.object(fields));
            assertEquals("up", parsed.get("status"));
            assertEquals("7", parsed.get("count"));
        }
    }

    @Nested
    @DisplayName("reader: parseFlatObject()")
    class Reader {

        @Test
        @DisplayName("a quoted string value is unquoted")
        void quotedValuesAreUnquoted() {
            assertEquals(Map.of("name", "sensor-1"), Json.parseFlatObject("{\"name\": \"sensor-1\"}"));
        }

        @Test
        @DisplayName("a bare number is returned as its raw text, for the caller to parse")
        void numbersComeBackAsText() {
            // Returning text rather than a Number is what lets the handler decide that
            // "abc" is a 400 rather than having the reader guess.
            assertEquals("60.5", Json.parseFlatObject("{\"threshold\": 60.5}").get("threshold"));
            assertEquals("-12", Json.parseFlatObject("{\"threshold\":-12}").get("threshold"));
        }

        @Test
        @DisplayName("an empty object parses to an empty map")
        void emptyObjectIsEmptyMap() {
            assertTrue(Json.parseFlatObject("{}").isEmpty());
            assertTrue(Json.parseFlatObject("{   }").isEmpty());
        }

        @Test
        @DisplayName("surrounding whitespace is ignored")
        void surroundingWhitespaceIsIgnored() {
            assertEquals(Map.of("a", "1"), Json.parseFlatObject("  \n {\"a\" : 1}  \t "));
        }

        @Test
        @DisplayName("several fields are all returned, in order")
        void multipleFieldsAreParsed() {
            Map<String, String> parsed = Json.parseFlatObject("{\"a\": 1, \"b\": \"two\", \"c\": 3}");
            assertEquals(3, parsed.size());
            assertEquals("1", parsed.get("a"));
            assertEquals("two", parsed.get("b"));
            assertEquals("3", parsed.get("c"));
        }

        @Test
        @DisplayName("a later duplicate key wins, as in a JSON object")
        void duplicateKeysTakeTheLastValue() {
            assertEquals("2", Json.parseFlatObject("{\"a\": 1, \"a\": 2}").get("a"));
        }

        @ParameterizedTest
        @DisplayName("anything that is not a flat object is rejected, never half-parsed")
        @ValueSource(strings = {
            "",
            "   ",
            "not json",
            "[1,2]",
            "{\"a\": 1",
            "\"a\": 1}",
            "null",
            "{\"a\"}",
            "{\"\": 1}",
            "{: 1}",
        })
        void malformedBodiesAreRejected(String body) {
            assertThrows(IllegalArgumentException.class, () -> Json.parseFlatObject(body),
                    "body should have been rejected: " + body);
        }

        @Test
        @DisplayName("a null body is rejected")
        void nullBodyIsRejected() {
            assertThrows(NullPointerException.class, () -> Json.parseFlatObject(null));
        }

        /**
         * The documented scope limits, pinned as tests rather than left as prose. None of
         * these throw — they parse into something that is not what the caller meant, which
         * is exactly why the class javadoc says to take a dependency instead of growing it.
         * A future maintainer who "fixes" one of these will see these tests fail and read
         * the reasoning.
         */
        @Test
        @DisplayName("nesting is outside the supported subset and is not silently accepted as nesting")
        void nestingIsNotSupported() {
            // Splitting on ',' cuts the nested object in half, so what comes back is text
            // fragments rather than a structure. Asserting the fragments -- rather than
            // asserting a sensible parse -- is the point: this is the behaviour, and the
            // handler is written to never send a nested body here.
            Map<String, String> parsed = Json.parseFlatObject("{\"outer\": {\"a\": 1, \"b\": 2}}");
            assertEquals(2, parsed.size());
            assertEquals("{\"a\": 1", parsed.get("outer"));
            assertEquals("2}", parsed.get("b"));
        }

        @Test
        @DisplayName("an escaped quote inside a value is not decoded")
        void escapeSequencesAreNotDecoded() {
            // Only the surrounding quotes are stripped; \" stays as two characters. The
            // control plane's single numeric field never needs this.
            assertEquals("a\\\"b", Json.parseFlatObject("{\"k\": \"a\\\"b\"}").get("k"));
        }

        @Test
        @DisplayName("a comma inside a string value splits the field, as documented")
        void commasInsideStringsSplitFields() {
            assertThrows(IllegalArgumentException.class,
                    () -> Json.parseFlatObject("{\"k\": \"a,b\"}"),
                    "the fragment after the comma has no colon, so the body is refused");
        }
    }
}
