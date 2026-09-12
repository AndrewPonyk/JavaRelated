package com.stringalgorithms.model;

import java.util.Objects;

public final class MatchResult {
    private final String algorithm;
    private final String pattern;
    private final int startIndex;

    public MatchResult(String algorithm, String pattern, int startIndex) {
        this.algorithm = Objects.requireNonNull(algorithm, "algorithm must not be null");
        this.pattern = Objects.requireNonNull(pattern, "pattern must not be null");
        this.startIndex = startIndex;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public String getPattern() {
        return pattern;
    }

    public int getStartIndex() {
        return startIndex;
    }
}
