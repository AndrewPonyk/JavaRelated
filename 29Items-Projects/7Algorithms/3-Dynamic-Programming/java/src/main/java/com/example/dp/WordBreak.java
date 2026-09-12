package com.example.dp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class WordBreak {
    private WordBreak() {
    }

    public static Result memoized(String text, Set<String> dictionary) {
        return new Result("Word Break", "memoization", solve(text, dictionary, 0, new HashMap<>()), Map.of());
    }

    private static boolean solve(String text, Set<String> dictionary, int start, Map<Integer, Boolean> memo) {
        if (start == text.length()) {
            return true;
        }
        if (memo.containsKey(start)) {
            return memo.get(start);
        }
        for (String word : dictionary) {
            if (text.startsWith(word, start) && solve(text, dictionary, start + word.length(), memo)) {
                memo.put(start, true);
                return true;
            }
        }
        memo.put(start, false);
        return false;
    }

    public static Result tabulated(String text, Set<String> dictionary) {
        boolean[] dp = new boolean[text.length() + 1];
        int[] parent = new int[text.length() + 1];
        for (int i = 0; i < parent.length; i++) {
            parent[i] = -1;
        }
        dp[0] = true;
        for (int end = 1; end <= text.length(); end++) {
            for (int start = 0; start < end; start++) {
                if (dp[start] && dictionary.contains(text.substring(start, end))) {
                    dp[end] = true;
                    parent[end] = start;
                    break;
                }
            }
        }
        List<String> words = new ArrayList<>();
        for (int end = text.length(); end > 0 && parent[end] != -1; end = parent[end]) {
            words.add(text.substring(parent[end], end));
        }
        Collections.reverse(words);
        return new Result("Word Break", "tabulation", dp[text.length()], Map.of("words", dp[text.length()] ? words : List.of()));
    }

    public static Result spaceOptimized(String text, Set<String> dictionary) {
        Result result = tabulated(text, new HashSet<>(dictionary));
        return new Result("Word Break", "space optimized", result.value(), result.details());
    }
}
