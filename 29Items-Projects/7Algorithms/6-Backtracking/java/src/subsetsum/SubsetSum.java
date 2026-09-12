package subsetsum;

import java.util.*;

public class SubsetSum {

    private final int[] set;
    private final int target;
    private final List<List<Integer>> solutions = new ArrayList<>();

    public SubsetSum(int[] set, int target) {
        this.set = set;
        this.target = target;
    }

    public void solveAndPrint() {
        System.out.println("Set: " + Arrays.toString(set));
        System.out.println("Target sum: " + target);
        System.out.println();

        backtrack(0, 0, new ArrayList<>());

        if (solutions.isEmpty()) {
            System.out.println("No subset sums to " + target + ".");
        } else {
            System.out.printf("Found %d subset(s):%n", solutions.size());
            for (int i = 0; i < solutions.size(); i++) {
                System.out.printf("  #%d: %s = %d%n", i + 1, solutions.get(i), target);
            }
        }
    }

    private void backtrack(int index, int currentSum, List<Integer> current) {
        if (currentSum == target && !current.isEmpty()) {
            solutions.add(new ArrayList<>(current));
        }
        for (int i = index; i < set.length; i++) {
            if (currentSum + set[i] <= target) {
                current.add(set[i]);
                backtrack(i + 1, currentSum + set[i], current);
                current.remove(current.size() - 1);
            }
        }
    }
}
