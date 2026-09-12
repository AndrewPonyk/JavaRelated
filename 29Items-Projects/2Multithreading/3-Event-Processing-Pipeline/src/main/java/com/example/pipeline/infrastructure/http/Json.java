package com.example.pipeline.infrastructure.http;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Minimal JSON <em>writer</em> and flat-object reader.
 *
 * <p><strong>Why hand-rolled:</strong> the project has zero runtime dependencies, and the
 * control plane emits three fixed shapes and accepts one field. Adding Jackson (plus its
 * transitive jars, plus its CVE feed) to serialise a handful of numbers is a worse trade
 * than 80 lines you can read in full.
 *
 * <p><strong>Scope limits, stated plainly</strong> so nobody mistakes this for a JSON
 * library: the reader handles a <em>flat</em> object of string and number values, with no
 * nesting, no arrays and no escape sequences beyond {@code \"}. Anything else is rejected
 * rather than half-parsed. If a richer request body is ever needed, take the dependency;
 * do not grow this class.
 *
 * <p>The writer escapes output properly, because that side faces a browser: an unescaped
 * quote or control character in a value would be an injection into the response, not
 * merely a formatting bug.
 */
public final class Json {

    private Json() {
    }

    /** Renders a flat map as a JSON object; {@code Number} and {@code Boolean} stay unquoted. */
    public static String object(Map<String, Object> fields) {
        Objects.requireNonNull(fields, "fields");
        StringBuilder out = new StringBuilder(128).append('{');
        boolean first = true;
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            out.append('"').append(escape(entry.getKey())).append("\":").append(value(entry.getValue()));
        }
        return out.append('}').toString();
    }

    /** Renders a single value: numbers and booleans bare, everything else as an escaped string. */
    public static String value(Object raw) {
        if (raw == null) {
            return "null";
        }
        if (raw instanceof Boolean bool) {
            return bool.toString();
        }
        if (raw instanceof Double || raw instanceof Float) {
            double number = ((Number) raw).doubleValue();
            // JSON has no Infinity or NaN literal; emitting one produces a body that no
            // strict parser will accept.
            if (!Double.isFinite(number)) {
                return "null";
            }
            return String.format(Locale.ROOT, "%.6f", number);
        }
        if (raw instanceof Number number) {
            return number.toString();
        }
        return '"' + escape(raw.toString()) + '"';
    }

    /**
     * Parses a flat JSON object into a string map.
     *
     * @param body request body
     * @return field name to raw text value
     * @throws IllegalArgumentException if the body is not a flat object this reader supports
     */
    public static Map<String, String> parseFlatObject(String body) {
        Objects.requireNonNull(body, "body");
        String trimmed = body.strip();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            throw new IllegalArgumentException("body must be a JSON object");
        }
        String inner = trimmed.substring(1, trimmed.length() - 1).strip();
        Map<String, String> fields = new LinkedHashMap<>();
        if (inner.isEmpty()) {
            return fields;
        }
        for (String pair : inner.split(",")) {
            int colon = pair.indexOf(':');
            if (colon < 0) {
                throw new IllegalArgumentException("malformed field: " + pair.strip());
            }
            String key = unquote(pair.substring(0, colon).strip());
            String value = unquote(pair.substring(colon + 1).strip());
            if (key.isEmpty()) {
                throw new IllegalArgumentException("field name must not be empty");
            }
            fields.put(key, value);
        }
        return fields;
    }

    private static String unquote(String token) {
        if (token.length() >= 2 && token.startsWith("\"") && token.endsWith("\"")) {
            return token.substring(1, token.length() - 1);
        }
        return token;
    }

    /** Escapes the characters JSON forbids in a string literal, plus other control characters. */
    private static String escape(String text) {
        StringBuilder out = new StringBuilder(text.length() + 8);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }
}
