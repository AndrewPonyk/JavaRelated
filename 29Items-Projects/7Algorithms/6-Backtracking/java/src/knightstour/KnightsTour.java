package knightstour;

public class KnightsTour {

    private final int n;
    private final int[][] board;
    private final int[] dx = {-2, -1, 1, 2, 2, 1, -1, -2};
    private final int[] dy = {1, 2, 2, 1, -1, -2, -2, -1};
    private boolean solved = false;

    public KnightsTour(int n) {
        this.n = n;
        this.board = new int[n][n];
    }

    public void solveAndPrint() {
        System.out.printf("Board: %dx%d%n%n", n, n);
        board[0][0] = 1;
        solved = backtrack(0, 0, 2);

        if (solved) {
            printBoard();
        } else {
            System.out.println("No Knight's Tour exists for n=" + n);
        }
    }

    private boolean backtrack(int row, int col, int moveNum) {
        if (moveNum > n * n) return true;

        for (int i = 0; i < 8; i++) {
            int nr = row + dx[i];
            int nc = col + dy[i];
            if (isValid(nr, nc)) {
                board[nr][nc] = moveNum;
                if (backtrack(nr, nc, moveNum + 1)) return true;
                board[nr][nc] = 0;
            }
        }
        return false;
    }

    private boolean isValid(int r, int c) {
        return r >= 0 && r < n && c >= 0 && c < n && board[r][c] == 0;
    }

    private void printBoard() {
        int width = String.valueOf(n * n).length() + 1;
        for (int r = 0; r < n; r++) {
            for (int c = 0; c < n; c++) {
                System.out.printf("%" + width + "d", board[r][c]);
            }
            System.out.println();
        }
    }
}
