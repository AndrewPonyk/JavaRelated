package ratinmaze;

import java.util.*;

public class RatInMaze {

    private final int[][] maze;
    private final int n;
    private final List<int[][]> solutions = new ArrayList<>();

    private static final int[] DR = {0, 1, 0, -1};
    private static final int[] DC = {1, 0, -1, 0};

    public RatInMaze(int[][] maze) {
        if (maze == null || maze.length == 0)
            throw new IllegalArgumentException("Maze must not be empty");
        this.maze = maze;
        this.n = maze.length;
    }

    public void solveAndPrint() {
        int[][] path = new int[n][n];
        if (maze[0][0] == 1) {
            path[0][0] = 1;
            backtrack(path, 0, 0);
        }

        System.out.printf("Found %d path(s) in %dx%d maze%n%n", solutions.size(), n, n);

        int limit = Math.min(solutions.size(), 4);
        for (int i = 0; i < limit; i++) {
            System.out.printf("Path #%d:%n", i + 1);
            printPath(solutions.get(i));
            System.out.println();
        }
        if (solutions.size() > limit) {
            System.out.printf("... and %d more.%n", solutions.size() - limit);
        }
    }

    private void backtrack(int[][] path, int row, int col) {
        if (row == n - 1 && col == n - 1) {
            solutions.add(copyPath(path));
            return;
        }
        for (int d = 0; d < 4; d++) {
            int nr = row + DR[d], nc = col + DC[d];
            if (isSafe(path, nr, nc)) {
                path[nr][nc] = 1;
                backtrack(path, nr, nc);
                path[nr][nc] = 0;
            }
        }
    }

    private boolean isSafe(int[][] path, int r, int c) {
        return r >= 0 && r < n && c >= 0 && c < n
                && maze[r][c] == 1 && path[r][c] == 0;
    }

    private int[][] copyPath(int[][] path) {
        int[][] copy = new int[n][n];
        for (int i = 0; i < n; i++) copy[i] = path[i].clone();
        return copy;
    }

    private void printPath(int[][] path) {
        for (int r = 0; r < n; r++) {
            StringBuilder sb = new StringBuilder("|");
            for (int c = 0; c < n; c++) {
                if (maze[r][c] == 0) {
                    sb.append(" # ");
                } else if (path[r][c] == 1) {
                    sb.append(" * ");
                } else {
                    sb.append(" . ");
                }
            }
            sb.append("|");
            System.out.println(sb);
        }
    }
}
