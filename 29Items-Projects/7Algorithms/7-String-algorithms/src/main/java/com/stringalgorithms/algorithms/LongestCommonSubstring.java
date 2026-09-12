package com.stringalgorithms.algorithms;

import java.util.Objects;

public final class LongestCommonSubstring {
    private LongestCommonSubstring() {
    }

    public static String find(String first, String second) {
        Objects.requireNonNull(first, "first must not be null");
        Objects.requireNonNull(second, "second must not be null");

        int[][] lengths = new int[first.length() + 1][second.length() + 1];
        int bestLength = 0;
        int bestEnd = 0;

        for (int i = 1; i <= first.length(); i++) {
            for (int j = 1; j <= second.length(); j++) {
                if (first.charAt(i - 1) == second.charAt(j - 1)) {
                    lengths[i][j] = lengths[i - 1][j - 1] + 1;
                    if (lengths[i][j] > bestLength) {
                        bestLength = lengths[i][j];
                        bestEnd = i;
                    }
                }
            }
        }

        return first.substring(bestEnd - bestLength, bestEnd);
    }

    public static int length(String first, String second) {
        return find(first, second).length();
    }
}
