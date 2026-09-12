package nqueens;

import java.util.*;

public class NQueens {

    private final int n;
    private final List<int[]> solutions = new ArrayList<>();

    public NQueens(int n) {
        if (n <= 0) throw new IllegalArgumentException("Board size must be > 0");
        this.n = n;
    }

    public void solveAndPrint() {
        solve(new int[n], 0);
        System.out.printf("Found %d solution(s) for n=%d%n%n", solutions.size(), n);

        int limit = Math.min(solutions.size(), 4);
        for (int i = 0; i < limit; i++) {
            System.out.printf("Solution #%d:%n", i + 1);
            printBoard(solutions.get(i));
            System.out.println();
        }
        if (solutions.size() > limit) {
            System.out.printf("... and %d more solutions.%n", solutions.size() - limit);
        }
    }

    private void solve(int[] queens, int row) {
        if (row == n) {
            solutions.add(queens.clone());
            return;
        }
        for (int col = 0; col < n; col++) {
            if (isSafe(queens, row, col)) {
                queens[row] = col;
                solve(queens, row + 1);
            }
        }
    }

    private boolean isSafe(int[] queens, int row, int col) {
        for (int r = 0; r < row; r++) {
            if (queens[r] == col || Math.abs(queens[r] - col) == row - r) {
                return false;
            }
        }
        return true;
    }

    private void printBoard(int[] queens) {
        for (int r = 0; r < n; r++) {
            StringBuilder sb = new StringBuilder("|");
            for (int c = 0; c < n; c++) {
                sb.append(queens[r] == c ? " Q " : " . ");
            }
            sb.append("|");
            System.out.println(sb);
        }
    }
}
