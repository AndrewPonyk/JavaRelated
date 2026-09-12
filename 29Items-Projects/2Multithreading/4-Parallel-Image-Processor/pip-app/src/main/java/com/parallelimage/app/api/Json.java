package com.parallelimage.app.api;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Minimal JSON writing and flat-object reading. Not a general-purpose library, on purpose.
 *
 * <h2>Why not Jackson</h2>
 * The control API has four endpoints and exchanges objects that are two levels deep. Jackson would add
 * three jars to a distribution whose whole selling point is "unzip and run", a reflective warm-up on the
 * first request, and — the part that actually matters — a deserializer that will happily construct
 * arbitrary types from attacker-controlled input if a future change ever enables polymorphic typing. A
 * hundred lines that can only produce strings, numbers, booleans and maps has none of those properties.
 *
 * <p>The rule that keeps this honest: <b>if the wire format ever needs nesting beyond one level, or a
 * schema, replace this class rather than growing it.</b> Hand-written parsers grow into liabilities
 * exactly at the point where they start handling nesting.
 *
 * <h2>What {@link #parseFlatObject} does and does not accept</h2>
 * It accepts a flat JSON object whose values are strings, numbers, booleans or {@code null} — which is the
 * entire shape of a batch submission. It rejects nested objects and arrays with an explanatory error
 * rather than silently mis-parsing them. It is not a JSON validator: it is strict enough that malformed
 * input is refused, and no stricter, because the alternative to "refused" here is a 400 either way.
 */
public final class Json {

    private Json() {
    }

    // ------------------------------------------------------------------------
    //  Writing
    // ------------------------------------------------------------------------

    /**
     * Renders a flat map as a JSON object.
     *
     * <p>Values are dispatched by runtime type: {@code null}, {@link Number}, {@link Boolean},
     * {@link Map} (one level), {@link List} of scalars, everything else as a quoted string. That last
     * fallback is why a {@code Path} or an enum needs no special case, and why nothing can accidentally
     * serialise as an object graph.
     */
    public static String object(Map<String, ?> fields) {
        StringBuilder out = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, ?> entry : fields.entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            out.append(quote(entry.getKey())).append(':').append(value(entry.getValue()));
        }
        return out.append('}').toString();
    }

    /** Renders a list of already-rendered JSON fragments as an array. */
    public static String array(List<String> rendered) {
        return "[" + String.join(",", rendered) + "]";
    }

    private static String value(Object raw) {
        if (raw == null) {
            return "null";
        }
        if (raw instanceof Number number) {
            // NaN and infinity are not JSON. A duration divided by a zero job count is exactly how one
            // gets here, and emitting bare NaN produces a parse error in the client rather than in us.
            double d = number.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                return "null";
            }
            return number.toString();
        }
        if (raw instanceof Boolean bool) {
            return bool.toString();
        }
        if (raw instanceof Map<?, ?> nested) {
            @SuppressWarnings("unchecked")
            Map<String, ?> typed = (Map<String, ?>) nested;
            return object(typed);
        }
        if (raw instanceof List<?> list) {
            return array(list.stream().map(Json::value).toList());
        }
        return quote(raw.toString());
    }

    /**
     * Quotes and escapes a string for JSON.
     *
     * <p>Escapes the two mandatory characters, the five shorthand control characters, and everything below
     * {@code 0x20} as {@code \\uXXXX}. Unescaped control characters are the classic hand-rolled-JSON bug:
     * an exception message containing a newline — which most stack-trace-derived messages do — produces a
     * response that no parser accepts, and the client reports a transport error rather than the failure
     * that was being described.
     */
    public static String quote(String raw) {
        StringBuilder out = new StringBuilder(raw.length() + 2).append('"');
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20) {
                        out.append("\\u%04x".formatted((int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    // ------------------------------------------------------------------------
    //  Reading
    // ------------------------------------------------------------------------

    /** Thrown for input this class refuses to read. Always maps to HTTP 400, never to 500. */
    public static final class JsonException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;

        public JsonException(String message) {
            super(message);
        }

        /**
         * For a parse failure that a lower-level exception detected first.
         *
         * <p>The cause never reaches the client — the controller answers 400 with
         * {@link #getMessage()} only — but it reaches the log, and "which of the four numeric
         * conversions in this parser threw" is not a question the message can answer.
         */
        public JsonException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Reads a flat JSON object into a string map. Values are returned as their source text, unquoted.
     *
     * <p>Everything is a {@code String} because the caller — {@code JobRequestValidator} — has to parse
     * and range-check each field anyway. Returning {@code Object} would mean every read site started with
     * an {@code instanceof} chain, and a client sending {@code "parallelism": "8"} instead of
     * {@code 8} would get a type error rather than the batch it asked for.
     *
     * @throws JsonException on malformed input, nesting, or a duplicate key
     */
    public static Map<String, String> parseFlatObject(String text) {
        String body = text == null ? "" : text.trim();
        if (body.isEmpty()) {
            return Map.of();
        }
        if (!body.startsWith("{") || !body.endsWith("}")) {
            throw new JsonException("expected a JSON object");
        }

        Map<String, String> fields = new java.util.LinkedHashMap<>();
        Cursor cursor = new Cursor(body, 1);

        cursor.skipWhitespace();
        if (cursor.peek() == '}') {
            return Map.of();
        }

        while (true) {
            cursor.skipWhitespace();
            String key = cursor.readString();
            cursor.skipWhitespace();
            cursor.expect(':');
            cursor.skipWhitespace();
            String value = cursor.readValue();
            // Rejected rather than last-wins. A duplicate key means the client is generating the body
            // with string concatenation, and quietly picking one of the two values hides a real bug in it.
            if (fields.put(key, value) != null) {
                throw new JsonException("duplicate key " + key);
            }
            cursor.skipWhitespace();
            char next = cursor.next();
            if (next == '}') {
                return Map.copyOf(fields);
            }
            if (next != ',') {
                throw new JsonException("expected ',' or '}' at offset " + cursor.position());
            }
        }
    }

    /** A string with a read position. Package-private inner class; not part of the API. */
    private static final class Cursor {
        private final String text;
        private int at;

        Cursor(String text, int at) {
            this.text = text;
            this.at = at;
        }

        int position() {
            return at;
        }

        void skipWhitespace() {
            while (at < text.length() && Character.isWhitespace(text.charAt(at))) {
                at++;
            }
        }

        char peek() {
            require(at < text.length(), "unexpected end of input");
            return text.charAt(at);
        }

        char next() {
            char c = peek();
            at++;
            return c;
        }

        void expect(char expected) {
            char actual = next();
            require(actual == expected, "expected '" + expected + "' at offset " + (at - 1));
        }

        /** Reads a quoted string, resolving the escapes {@link Json#quote} produces. */
        String readString() {
            expect('"');
            StringBuilder out = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') {
                    return out.toString();
                }
                if (c != '\\') {
                    out.append(c);
                    continue;
                }
                char escape = next();
                switch (escape) {
                    case '"', '\\', '/' -> out.append(escape);
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'u' -> {
                        require(at + 4 <= text.length(), "truncated \\u escape");
                        String hex = text.substring(at, at + 4);
                        at += 4;
                        try {
                            out.append((char) Integer.parseInt(hex, 16));
                        } catch (NumberFormatException e) {
                            throw new JsonException("bad \\u escape: " + hex, e);
                        }
                    }
                    default -> throw new JsonException("unknown escape \\" + escape);
                }
            }
        }

        /** Reads one value as text. Nesting is refused rather than skipped over. */
        String readValue() {
            char c = peek();
            if (c == '"') {
                return readString();
            }
            if (c == '{' || c == '[') {
                throw new JsonException("nested " + (c == '{' ? "objects" : "arrays")
                        + " are not supported by this endpoint");
            }
            int start = at;
            while (at < text.length() && ",}".indexOf(text.charAt(at)) < 0) {
                at++;
            }
            String raw = text.substring(start, at).trim();
            require(!raw.isEmpty(), "missing value at offset " + start);
            require(isLiteral(raw), "'" + raw + "' is not a JSON value (at offset " + start + ")");
            return raw;
        }

        /**
         * Whether unquoted text is one of the three JSON literals or a finite number.
         *
         * <p>Checked rather than returned as-is because the scan above stops only at a comma or a closing
         * brace. Without this, a body missing a comma — {@code "a":1 "b":2} — parses as the single
         * field {@code a} with the text {@code 1 "b":2}, and {@code b} is silently dropped. A field the
         * client sent and this endpoint ignored is exactly what {@code JobRequestValidator}'s
         * unknown-field check exists to make impossible, so it cannot be allowed in one layer lower.
         *
         * <p>Non-finite is refused for the same reason {@link Json#value} writes it as {@code null}:
         * {@code NaN} and {@code Infinity} are not JSON, and {@code Double.parseDouble} accepts both.
         */
        private static boolean isLiteral(String raw) {
            if (raw.equals("true") || raw.equals("false") || raw.equals("null")) {
                return true;
            }
            try {
                return Double.isFinite(Double.parseDouble(raw));
            } catch (NumberFormatException e) {
                return false;
            }
        }

        private static void require(boolean condition, String message) {
            if (!condition) {
                throw new JsonException(message);
            }
        }
    }

    /** Reads a field as text, if present and non-blank. */
    public static Optional<String> text(Map<String, String> fields, String key) {
        String value = fields.get(key);
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value.trim());
    }
}
