package wordsearch;

import java.util.*;
import java.util.stream.Collectors;

public class WordSearch {

    private final char[][] board;
    private final int rows, cols;
    private final List<String> words;
    private final Set<String> found = new LinkedHashSet<>();

    private static final int[] DR = {-1, -1, -1, 0, 0, 1, 1, 1};
    private static final int[] DC = {-1, 0, 1, -1, 1, -1, 0, 1};

    public WordSearch(char[][] board, List<String> words) {
        if (board == null || board.length == 0)
            throw new IllegalArgumentException("Board must not be empty");
        this.board = board;
        this.rows = board.length;
        this.cols = board[0].length;
        this.words = words;
    }

    public void solveAndPrint() {
        printBoard();
        System.out.println("Searching for: " + words);
        System.out.println();

        for (String word : words) {
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    boolean[][] visited = new boolean[rows][cols];
                    backtrack(word, 0, r, c, visited);
                }
            }
        }

        System.out.printf("Found %d word(s):%n", found.size());
        for (String w : found) {
            System.out.println("  - " + w);
        }
        if (found.size() < words.size()) {
            System.out.println("Not found: " + words.stream()
                    .filter(w -> !found.contains(w)).collect(Collectors.toList()));
        }
    }

    private boolean backtrack(String word, int idx, int r, int c, boolean[][] visited) {
        if (board[r][c] != word.charAt(idx)) return false;
        if (idx == word.length() - 1) {
            found.add(word);
            return true;
        }
        visited[r][c] = true;
        for (int d = 0; d < 8; d++) {
            int nr = r + DR[d], nc = c + DC[d];
            if (nr >= 0 && nr < rows && nc >= 0 && nc < cols && !visited[nr][nc]) {
                if (backtrack(word, idx + 1, nr, nc, visited)) {
                    visited[r][c] = false;
                    return true;
                }
            }
        }
        visited[r][c] = false;
        return false;
    }

    private void printBoard() {
        System.out.println("Board:");
        for (char[] row : board) {
            StringBuilder sb = new StringBuilder("|");
            for (char ch : row) sb.append(" ").append(ch).append(" ");
            sb.append("|");
            System.out.println(sb);
        }
        System.out.println();
    }
}
