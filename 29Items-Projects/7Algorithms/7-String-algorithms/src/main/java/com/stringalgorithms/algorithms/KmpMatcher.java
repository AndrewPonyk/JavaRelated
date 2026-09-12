package com.stringalgorithms.algorithms;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class KmpMatcher {
    private KmpMatcher() {
    }

    public static List<Integer> search(String text, String pattern) {
        Objects.requireNonNull(text, "text must not be null");
        Objects.requireNonNull(pattern, "pattern must not be null");
        if (pattern.isEmpty()) {
            throw new IllegalArgumentException("pattern must not be empty");
        }

        int[] lps = buildLongestPrefixSuffix(pattern);
        List<Integer> matches = new ArrayList<>();
        int textIndex = 0;
        int patternIndex = 0;

        while (textIndex < text.length()) {
            if (text.charAt(textIndex) == pattern.charAt(patternIndex)) {
                textIndex++;
                patternIndex++;
            }

            if (patternIndex == pattern.length()) {
                matches.add(textIndex - patternIndex);
                patternIndex = lps[patternIndex - 1];
            } else if (textIndex < text.length()
                    && text.charAt(textIndex) != pattern.charAt(patternIndex)) {
                if (patternIndex != 0) {
                    patternIndex = lps[patternIndex - 1];
                } else {
                    textIndex++;
                }
            }
        }

        return matches;
    }

    static int[] buildLongestPrefixSuffix(String pattern) {
        int[] lps = new int[pattern.length()];
        int length = 0;
        int index = 1;

        while (index < pattern.length()) {
            if (pattern.charAt(index) == pattern.charAt(length)) {
                lps[index] = ++length;
                index++;
            } else if (length != 0) {
                length = lps[length - 1];
            } else {
                lps[index] = 0;
                index++;
            }
        }

        return lps;
    }
}
