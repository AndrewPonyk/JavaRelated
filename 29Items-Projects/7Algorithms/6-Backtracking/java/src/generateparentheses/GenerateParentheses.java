package generateparentheses;

import java.util.*;

public class GenerateParentheses {

    private final int n;
    private final List<String> solutions = new ArrayList<>();

    public GenerateParentheses(int n) {
        if (n <= 0) throw new IllegalArgumentException("n must be > 0");
        this.n = n;
    }

    public void solveAndPrint() {
        backtrack(new StringBuilder(), 0, 0);
        System.out.printf("Found %d valid combination(s) for n=%d%n%n", solutions.size(), n);

        int limit = Math.min(solutions.size(), 8);
        for (int i = 0; i < limit; i++) {
            System.out.printf("#%d: %s%n", i + 1, solutions.get(i));
        }
        if (solutions.size() > limit) {
            System.out.printf("... and %d more.%n", solutions.size() - limit);
        }
    }

    private void backtrack(StringBuilder current, int open, int close) {
        if (current.length() == n * 2) {
            solutions.add(current.toString());
            return;
        }
        if (open < n) {
            current.append('(');
            backtrack(current, open + 1, close);
            current.deleteCharAt(current.length() - 1);
        }
        if (close < open) {
            current.append(')');
            backtrack(current, open, close + 1);
            current.deleteCharAt(current.length() - 1);
        }
    }
}
