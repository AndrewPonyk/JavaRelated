package combinations;

import java.util.*;

public class Combinations {

    private final int[] set;
    private final int k;
    private final List<List<Integer>> results = new ArrayList<>();

    public Combinations(int[] set, int k) {
        this.set = set;
        this.k = k;
    }

    public void solveAndPrint() {
        System.out.println("Set: " + Arrays.toString(set));
        System.out.println("Combination size k=" + k);
        System.out.println();

        backtrack(0, new ArrayList<>());

        System.out.printf("Found %d combination(s):%n", results.size());
        for (int i = 0; i < results.size(); i++) {
            System.out.printf("  #%d: %s%n", i + 1, results.get(i));
        }
    }

    private void backtrack(int start, List<Integer> current) {
        if (current.size() == k) {
            results.add(new ArrayList<>(current));
            return;
        }
        for (int i = start; i < set.length; i++) {
            current.add(set[i]);
            backtrack(i + 1, current);
            current.remove(current.size() - 1);
        }
    }
}
