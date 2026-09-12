package sudoku;

public class SudokuSolver {

    private final int[][] board;
    private boolean solved = false;

    public SudokuSolver(int[][] board) {
        this.board = new int[9][9];
        for (int i = 0; i < 9; i++) {
            this.board[i] = board[i].clone();
        }
    }

    public void solveAndPrint() {
        System.out.println("Input puzzle:");
        printBoard();

        solved = solve(0, 0);

        if (solved) {
            System.out.println("\nSolved:");
            printBoard();
        } else {
            System.out.println("\nNo solution exists.");
        }
    }

    private boolean solve(int row, int col) {
        if (row == 9) return true;
        int nextRow = (col == 8) ? row + 1 : row;
        int nextCol = (col + 1) % 9;

        if (board[row][col] != 0) {
            return solve(nextRow, nextCol);
        }

        for (int num = 1; num <= 9; num++) {
            if (isValid(row, col, num)) {
                board[row][col] = num;
                if (solve(nextRow, nextCol)) return true;
                board[row][col] = 0;
            }
        }
        return false;
    }

    private boolean isValid(int row, int col, int num) {
        for (int i = 0; i < 9; i++) {
            if (board[row][i] == num || board[i][col] == num) return false;
        }
        int boxRow = (row / 3) * 3, boxCol = (col / 3) * 3;
        for (int r = boxRow; r < boxRow + 3; r++) {
            for (int c = boxCol; c < boxCol + 3; c++) {
                if (board[r][c] == num) return false;
            }
        }
        return true;
    }

    private void printBoard() {
        System.out.println("+-------+-------+-------+");
        for (int r = 0; r < 9; r++) {
            if (r == 3 || r == 6) System.out.println("+-------+-------+-------+");
            StringBuilder sb = new StringBuilder("|");
            for (int c = 0; c < 9; c++) {
                sb.append(board[r][c] == 0 ? " . " : " " + board[r][c] + " ");
                if (c == 2 || c == 5) sb.append("|");
            }
            sb.append("|");
            System.out.println(sb);
        }
        System.out.println("+-------+-------+-------+");
    }
}
