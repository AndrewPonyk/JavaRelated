package tugofwar;

import java.util.*;

public class TugOfWar {

    private final int[] arr;
    private final int n;
    private int minDiff = Integer.MAX_VALUE;
    private List<Integer> bestSet1;
    private List<Integer> bestSet2;

    public TugOfWar(int[] arr) {
        if (arr == null || arr.length < 2)
            throw new IllegalArgumentException("Array must have at least 2 elements");
        this.arr = arr.clone();
        this.n = arr.length;
    }

    public void solveAndPrint() {
        System.out.println("Input: " + Arrays.toString(arr));

        backtrack(new ArrayList<>(), new ArrayList<>(), 0);

        System.out.printf("Minimum difference: %d%n%n", minDiff);
        int sum1 = bestSet1.stream().mapToInt(x -> x).sum();
        int sum2 = bestSet2.stream().mapToInt(x -> x).sum();
        System.out.printf("Set 1: %s (sum = %d)%n", bestSet1, sum1);
        System.out.printf("Set 2: %s (sum = %d)%n", bestSet2, sum2);
    }

    private void backtrack(List<Integer> set1, List<Integer> set2, int index) {
        if (index == n) {
            int diff = Math.abs(
                    set1.stream().mapToInt(x -> x).sum() -
                    set2.stream().mapToInt(x -> x).sum());
            if (diff < minDiff) {
                minDiff = diff;
                bestSet1 = new ArrayList<>(set1);
                bestSet2 = new ArrayList<>(set2);
            }
            return;
        }

        // Prune: if current difference already exceeds best, skip
        if (Math.abs(set1.stream().mapToInt(x -> x).sum() -
                     set2.stream().mapToInt(x -> x).sum()) > minDiff) {
            return;
        }

        set1.add(arr[index]);
        backtrack(set1, set2, index + 1);
        set1.remove(set1.size() - 1);

        set2.add(arr[index]);
        backtrack(set1, set2, index + 1);
        set2.remove(set2.size() - 1);
    }
}
