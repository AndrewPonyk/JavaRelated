package lettercombinations;

import java.util.*;

public class LetterCombinations {

    private static final String[] MAP = {
        "", "", "abc", "def", "ghi", "jkl", "mno", "pqrs", "tuv", "wxyz"
    };

    private final String digits;
    private final List<String> solutions = new ArrayList<>();

    public LetterCombinations(String digits) {
        if (digits == null || digits.isEmpty())
            throw new IllegalArgumentException("Digits must not be empty");
        this.digits = digits;
    }

    public void solveAndPrint() {
        backtrack(new StringBuilder(), 0);
        System.out.printf("Found %d combination(s) for digits \"%s\"%n%n", solutions.size(), digits);

        int limit = Math.min(solutions.size(), 8);
        for (int i = 0; i < limit; i++) {
            System.out.printf("#%d: %s%n", i + 1, solutions.get(i));
        }
        if (solutions.size() > limit) {
            System.out.printf("... and %d more.%n", solutions.size() - limit);
        }
    }

    private void backtrack(StringBuilder current, int index) {
        if (index == digits.length()) {
            solutions.add(current.toString());
            return;
        }
        String letters = MAP[digits.charAt(index) - '0'];
        for (char ch : letters.toCharArray()) {
            current.append(ch);
            backtrack(current, index + 1);
            current.deleteCharAt(current.length() - 1);
        }
    }
}
