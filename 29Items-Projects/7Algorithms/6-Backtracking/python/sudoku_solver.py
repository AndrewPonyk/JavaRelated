"""Sudoku Solver — classic 9×9 backtracking solver."""


class SudokuSolver:
    def __init__(self, board: list[list[int]]):
        self.board = [row[:] for row in board]

    def solve_and_print(self) -> None:
        print("Input puzzle:")
        self._print_board()

        solved = self._solve(0, 0)
        print("\nSolved:" if solved else "\nNo solution exists.")
        if solved:
            self._print_board()

    def _solve(self, row: int, col: int) -> bool:
        if row == 9:
            return True
        next_row = row + 1 if col == 8 else row
        next_col = (col + 1) % 9

        if self.board[row][col] != 0:
            return self._solve(next_row, next_col)

        for num in range(1, 10):
            if self._is_valid(row, col, num):
                self.board[row][col] = num
                if self._solve(next_row, next_col):
                    return True
                self.board[row][col] = 0
        return False

    def _is_valid(self, row: int, col: int, num: int) -> bool:
        for i in range(9):
            if self.board[row][i] == num or self.board[i][col] == num:
                return False
        br, bc = (row // 3) * 3, (col // 3) * 3
        for r in range(br, br + 3):
            for c in range(bc, bc + 3):
                if self.board[r][c] == num:
                    return False
        return True

    def _print_board(self) -> None:
        print("┌───────┬───────┬───────┐")
        for r in range(9):
            if r in (3, 6):
                print("├───────┼───────┼───────┤")
            row = "│"
            for c in range(9):
                cell = "." if self.board[r][c] == 0 else str(self.board[r][c])
                row += f" {cell} "
                if c in (2, 5):
                    row += "│"
            row += "│"
            print(row)
        print("└───────┴───────┴───────┘")
