package com.example.dp;

import java.util.Map;
import java.util.stream.Collectors;

public final class Result {
    private final String algorithm;
    private final String variant;
    private final Object value;
    private final Map<String, Object> details;

    public Result(String algorithm, String variant, Object value, Map<String, Object> details) {
        this.algorithm = algorithm;
        this.variant = variant;
        this.value = value;
        this.details = details;
    }

    public Object value() {
        return value;
    }

    public Map<String, Object> details() {
        return details;
    }

    public String describe() {
        if (details == null || details.isEmpty()) {
            return String.format("%s [%s] -> %s", algorithm, variant, value);
        }
        String suffix = details.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(", "));
        return String.format("%s [%s] -> %s | %s", algorithm, variant, value, suffix);
    }
}
