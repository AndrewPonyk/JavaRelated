package com.example.dp;

import java.util.HashMap;
import java.util.Map;

public final class LongestPalindromicSubstring {
    private LongestPalindromicSubstring() {
    }

    public static Result memoized(String text) {
        Map<String, Boolean> memo = new HashMap<>();
        String best = "";
        for (int left = 0; left < text.length(); left++) {
            for (int right = left; right < text.length(); right++) {
                if (right - left + 1 > best.length() && isPalindrome(text, left, right, memo)) {
                    best = text.substring(left, right + 1);
                }
            }
        }
        return new Result("Longest Palindrome", "memoization", best.length(), Map.of("substring", best));
    }

    private static boolean isPalindrome(String text, int left, int right, Map<String, Boolean> memo) {
        if (left >= right) {
            return true;
        }
        String key = left + ":" + right;
        if (memo.containsKey(key)) {
            return memo.get(key);
        }
        boolean value = text.charAt(left) == text.charAt(right) && isPalindrome(text, left + 1, right - 1, memo);
        memo.put(key, value);
        return value;
    }

    public static Result tabulated(String text) {
        if (text.isEmpty()) {
            return new Result("Longest Palindrome", "tabulation", 0, Map.of("substring", ""));
        }
        boolean[][] dp = new boolean[text.length()][text.length()];
        int start = 0;
        int best = 1;
        for (int right = 0; right < text.length(); right++) {
            for (int left = 0; left <= right; left++) {
                if (text.charAt(left) == text.charAt(right) && (right - left <= 2 || dp[left + 1][right - 1])) {
                    dp[left][right] = true;
                    if (right - left + 1 > best) {
                        start = left;
                        best = right - left + 1;
                    }
                }
            }
        }
        return new Result("Longest Palindrome", "tabulation", best, Map.of("substring", text.substring(start, start + best)));
    }

    public static Result spaceOptimized(String text) {
        int bestLeft = 0;
        int bestRight = 0;
        for (int center = 0; center < text.length(); center++) {
            int[] odd = expand(text, center, center);
            int[] even = expand(text, center, center + 1);
            if (odd[1] - odd[0] > bestRight - bestLeft) {
                bestLeft = odd[0];
                bestRight = odd[1];
            }
            if (even[1] - even[0] > bestRight - bestLeft) {
                bestLeft = even[0];
                bestRight = even[1];
            }
        }
        String substring = text.substring(bestLeft, bestRight);
        return new Result("Longest Palindrome", "center expansion/space optimized", substring.length(), Map.of("substring", substring));
    }

    private static int[] expand(String text, int left, int right) {
        while (left >= 0 && right < text.length() && text.charAt(left) == text.charAt(right)) {
            left--;
            right++;
        }
        return new int[] {left + 1, right};
    }
}
