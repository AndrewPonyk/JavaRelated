package com.stringalgorithms.algorithms;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class RabinKarp {
    private static final int BASE = 256;
    private static final int MOD = 1_000_000_007;

    private RabinKarp() {
    }

    public static List<Integer> search(String text, String pattern) {
        Objects.requireNonNull(text, "text must not be null");
        Objects.requireNonNull(pattern, "pattern must not be null");
        if (pattern.isEmpty()) {
            throw new IllegalArgumentException("pattern must not be empty");
        }
        if (pattern.length() > text.length()) {
            return List.of();
        }

        long highestBasePower = 1;
        for (int i = 1; i < pattern.length(); i++) {
            highestBasePower = (highestBasePower * BASE) % MOD;
        }

        long patternHash = 0;
        long windowHash = 0;
        for (int i = 0; i < pattern.length(); i++) {
            patternHash = (patternHash * BASE + pattern.charAt(i)) % MOD;
            windowHash = (windowHash * BASE + text.charAt(i)) % MOD;
        }

        List<Integer> matches = new ArrayList<>();
        for (int start = 0; start <= text.length() - pattern.length(); start++) {
            if (patternHash == windowHash
                    && text.regionMatches(start, pattern, 0, pattern.length())) {
                matches.add(start);
            }

            if (start < text.length() - pattern.length()) {
                windowHash = removeLeadingCharacter(windowHash, text.charAt(start), highestBasePower);
                windowHash = (windowHash * BASE + text.charAt(start + pattern.length())) % MOD;
            }
        }

        return matches;
    }

    private static long removeLeadingCharacter(long hash, char leading, long highestBasePower) {
        long updated = hash - (leading * highestBasePower) % MOD;
        return updated < 0 ? updated + MOD : updated;
    }
}
