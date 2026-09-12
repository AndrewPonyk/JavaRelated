"""Knight's Tour — visit every square on a chessboard exactly once."""

DX = [-2, -1, 1, 2, 2, 1, -1, -2]
DY = [1, 2, 2, 1, -1, -2, -2, -1]


class KnightsTour:
    def __init__(self, n: int):
        self.n = n
        self.board = [[0] * n for _ in range(n)]

    def solve_and_print(self) -> None:
        print(f"Board: {self.n}×{self.n}\n")
        self.board[0][0] = 1

        if self._backtrack(0, 0, 2):
            self._print_board()
        else:
            print(f"No Knight's Tour exists for n={self.n}")

    def _backtrack(self, row: int, col: int, move: int) -> bool:
        if move > self.n * self.n:
            return True
        for i in range(8):
            nr, nc = row + DX[i], col + DY[i]
            if self._is_valid(nr, nc):
                self.board[nr][nc] = move
                if self._backtrack(nr, nc, move + 1):
                    return True
                self.board[nr][nc] = 0
        return False

    def _is_valid(self, r: int, c: int) -> bool:
        return 0 <= r < self.n and 0 <= c < self.n and self.board[r][c] == 0

    def _print_board(self) -> None:
        width = len(str(self.n * self.n)) + 1
        for row in self.board:
            print("".join(f"{v:{width}d}" for v in row))
