package boggle;

import java.util.*;

public class BoggleSolver {

    private final char[][] board;
    private final int rows, cols;
    private final Set<String> dictionary;
    private final Set<String> found = new TreeSet<>();

    private static final int[] DR = {-1, -1, -1, 0, 0, 1, 1, 1};
    private static final int[] DC = {-1, 0, 1, -1, 1, -1, 0, 1};

    public BoggleSolver(char[][] board, Set<String> dictionary) {
        if (board == null || board.length == 0)
            throw new IllegalArgumentException("Board must not be empty");
        this.board = board;
        this.rows = board.length;
        this.cols = board[0].length;
        this.dictionary = dictionary;
    }

    public void solveAndPrint() {
        printBoard();

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                boolean[][] visited = new boolean[rows][cols];
                backtrack(r, c, "", visited);
            }
        }

        System.out.printf("Dictionary: %s%n%n", dictionary);
        System.out.printf("Found %d word(s):%n", found.size());
        for (String w : found) {
            System.out.println("  - " + w);
        }
    }

    private void backtrack(int r, int c, String current, boolean[][] visited) {
        current += board[r][c];

        if (current.length() >= 3 && dictionary.contains(current.toLowerCase())) {
            found.add(current);
        }

        // Prune: if no dictionary word starts with current prefix, stop
        if (!hasPrefix(current.toLowerCase())) return;

        visited[r][c] = true;
        for (int d = 0; d < 8; d++) {
            int nr = r + DR[d], nc = c + DC[d];
            if (nr >= 0 && nr < rows && nc >= 0 && nc < cols && !visited[nr][nc]) {
                backtrack(nr, nc, current, visited);
            }
        }
        visited[r][c] = false;
    }

    private boolean hasPrefix(String prefix) {
        for (String word : dictionary) {
            if (word.startsWith(prefix)) return true;
        }
        return false;
    }

    private void printBoard() {
        System.out.println("Boggle Board:");
        for (char[] row : board) {
            StringBuilder sb = new StringBuilder("|");
            for (char ch : row) sb.append(" ").append(ch).append(" ");
            sb.append("|");
            System.out.println(sb);
        }
        System.out.println();
    }
}
