package combinationsum;

import java.util.*;

public class CombinationSum {

    private final int[] candidates;
    private final int target;
    private final List<List<Integer>> solutions = new ArrayList<>();

    public CombinationSum(int[] candidates, int target) {
        if (candidates == null || candidates.length == 0)
            throw new IllegalArgumentException("Candidates must not be empty");
        this.candidates = candidates.clone();
        Arrays.sort(this.candidates);
        this.target = target;
    }

    public void solveAndPrint() {
        backtrack(new ArrayList<>(), 0, 0);
        System.out.printf("Found %d combination(s) summing to %d%n%n", solutions.size(), target);

        int limit = Math.min(solutions.size(), 8);
        for (int i = 0; i < limit; i++) {
            List<Integer> sol = solutions.get(i);
            System.out.printf("#%d: %s = %d%n", i + 1, sol, sol.stream().mapToInt(x -> x).sum());
        }
        if (solutions.size() > limit) {
            System.out.printf("... and %d more.%n", solutions.size() - limit);
        }
    }

    private void backtrack(List<Integer> current, int start, int sum) {
        if (sum == target) {
            solutions.add(new ArrayList<>(current));
            return;
        }
        for (int i = start; i < candidates.length; i++) {
            if (sum + candidates[i] > target) break;
            current.add(candidates[i]);
            backtrack(current, i, sum + candidates[i]);
            current.remove(current.size() - 1);
        }
    }
}
