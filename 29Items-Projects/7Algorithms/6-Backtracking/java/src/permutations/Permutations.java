package permutations;

import java.util.*;

public class Permutations {

    private final int[] arr;
    private final List<List<Integer>> results = new ArrayList<>();

    public Permutations(int[] arr) {
        this.arr = arr.clone();
    }

    public void solveAndPrint() {
        System.out.println("Elements: " + Arrays.toString(arr));
        System.out.println();

        backtrack(0);

        System.out.printf("Found %d permutation(s):%n", results.size());
        for (int i = 0; i < results.size(); i++) {
            System.out.printf("  #%d: %s%n", i + 1, results.get(i));
        }
    }

    private void backtrack(int start) {
        if (start == arr.length) {
            List<Integer> perm = new ArrayList<>();
            for (int v : arr) perm.add(v);
            results.add(perm);
            return;
        }
        for (int i = start; i < arr.length; i++) {
            swap(start, i);
            backtrack(start + 1);
            swap(start, i);
        }
    }

    private void swap(int i, int j) {
        int tmp = arr[i];
        arr[i] = arr[j];
        arr[j] = tmp;
    }
}
