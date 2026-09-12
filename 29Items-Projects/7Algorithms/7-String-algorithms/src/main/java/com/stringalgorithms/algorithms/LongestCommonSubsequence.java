package com.stringalgorithms.algorithms;

import java.util.Objects;

public final class LongestCommonSubsequence {
    private LongestCommonSubsequence() {
    }

    public static String find(String first, String second) {
        Objects.requireNonNull(first, "first must not be null");
        Objects.requireNonNull(second, "second must not be null");

        int[][] lengths = new int[first.length() + 1][second.length() + 1];
        for (int i = 1; i <= first.length(); i++) {
            for (int j = 1; j <= second.length(); j++) {
                if (first.charAt(i - 1) == second.charAt(j - 1)) {
                    lengths[i][j] = lengths[i - 1][j - 1] + 1;
                } else {
                    lengths[i][j] = Math.max(lengths[i - 1][j], lengths[i][j - 1]);
                }
            }
        }

        StringBuilder result = new StringBuilder();
        int i = first.length();
        int j = second.length();
        while (i > 0 && j > 0) {
            if (first.charAt(i - 1) == second.charAt(j - 1)) {
                result.append(first.charAt(i - 1));
                i--;
                j--;
            } else if (lengths[i - 1][j] >= lengths[i][j - 1]) {
                i--;
            } else {
                j--;
            }
        }

        return result.reverse().toString();
    }

    public static int length(String first, String second) {
        return find(first, second).length();
    }
}
