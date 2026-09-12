package palindromepartitioning;

import java.util.*;

public class PalindromePartitioning {

    private final String s;
    private final List<List<String>> solutions = new ArrayList<>();

    public PalindromePartitioning(String s) {
        if (s == null || s.isEmpty())
            throw new IllegalArgumentException("String must not be empty");
        this.s = s;
    }

    public void solveAndPrint() {
        backtrack(new ArrayList<>(), 0);
        System.out.printf("Found %d partition(s) for \"%s\"%n%n", solutions.size(), s);

        int limit = Math.min(solutions.size(), 6);
        for (int i = 0; i < limit; i++) {
            System.out.printf("#%d: %s%n", i + 1, solutions.get(i));
        }
        if (solutions.size() > limit) {
            System.out.printf("... and %d more.%n", solutions.size() - limit);
        }
    }

    private void backtrack(List<String> current, int start) {
        if (start == s.length()) {
            solutions.add(new ArrayList<>(current));
            return;
        }
        for (int end = start + 1; end <= s.length(); end++) {
            String sub = s.substring(start, end);
            if (isPalindrome(sub)) {
                current.add(sub);
                backtrack(current, end);
                current.remove(current.size() - 1);
            }
        }
    }

    private boolean isPalindrome(String str) {
        int l = 0, r = str.length() - 1;
        while (l < r) {
            if (str.charAt(l++) != str.charAt(r--)) return false;
        }
        return true;
    }
}
