package com.stringalgorithms.algorithms;

import java.util.Objects;

public final class Manacher {
    private Manacher() {
    }

    public static String longestPalindrome(String text) {
        Objects.requireNonNull(text, "text must not be null");
        if (text.isEmpty()) {
            return "";
        }

        char[] transformed = transform(text);
        int[] radii = new int[transformed.length];
        int center = 0;
        int right = 0;
        int bestCenter = 0;
        int bestRadius = 0;

        for (int index = 0; index < transformed.length; index++) {
            int mirror = 2 * center - index;
            if (index < right) {
                radii[index] = Math.min(right - index, radii[mirror]);
            }

            while (index - radii[index] - 1 >= 0
                    && index + radii[index] + 1 < transformed.length
                    && transformed[index - radii[index] - 1] == transformed[index + radii[index] + 1]) {
                radii[index]++;
            }

            if (index + radii[index] > right) {
                center = index;
                right = index + radii[index];
            }

            if (radii[index] > bestRadius) {
                bestRadius = radii[index];
                bestCenter = index;
            }
        }

        int start = (bestCenter - bestRadius) / 2;
        return text.substring(start, start + bestRadius);
    }

    private static char[] transform(String text) {
        char[] transformed = new char[text.length() * 2 + 1];
        for (int i = 0; i < transformed.length; i++) {
            transformed[i] = i % 2 == 0 ? '#' : text.charAt(i / 2);
        }
        return transformed;
    }
}
