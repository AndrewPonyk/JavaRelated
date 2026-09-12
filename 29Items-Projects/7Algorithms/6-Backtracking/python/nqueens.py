"""N-Queens — place n queens on an n×n board with no mutual attacks."""


class NQueens:
    def __init__(self, n: int):
        if n <= 0:
            raise ValueError("Board size must be > 0")
        self.n = n
        self.solutions: list[list[int]] = []

    def solve_and_print(self) -> None:
        self._solve([], 0)
        print(f"Found {len(self.solutions)} solution(s) for n={self.n}\n")

        limit = min(len(self.solutions), 4)
        for i, sol in enumerate(self.solutions[:limit], 1):
            print(f"Solution #{i}:")
            self._print_board(sol)
            print()

        if len(self.solutions) > limit:
            print(f"... and {len(self.solutions) - limit} more solutions.")

    def _solve(self, queens: list[int], row: int) -> None:
        if row == self.n:
            self.solutions.append(queens[:])
            return
        for col in range(self.n):
            if self._is_safe(queens, row, col):
                queens.append(col)
                self._solve(queens, row + 1)
                queens.pop()

    def _is_safe(self, queens: list[int], row: int, col: int) -> bool:
        for r, c in enumerate(queens):
            if c == col or abs(c - col) == row - r:
                return False
        return True

    def _print_board(self, queens: list[int]) -> None:
        for r in range(self.n):
            row = "│"
            for c in range(self.n):
                row += " ♛ " if queens[r] == c else " · "
            row += "│"
            print(row)
