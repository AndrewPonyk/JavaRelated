"""Boggle Solver — find all dictionary words on a Boggle board (8 directions)."""

_DIRS = [(-1, -1), (-1, 0), (-1, 1), (0, -1), (0, 1), (1, -1), (1, 0), (1, 1)]


class BoggleSolver:
    def __init__(self, board: list[list[str]], dictionary: set[str]):
        if not board:
            raise ValueError("Board must not be empty")
        self.board = board
        self.rows = len(board)
        self.cols = len(board[0])
        self.dictionary = {w.lower() for w in dictionary}
        self.found: set[str] = set()

    def solve_and_print(self) -> None:
        self._print_board()

        for r in range(self.rows):
            for c in range(self.cols):
                visited = [[False] * self.cols for _ in range(self.rows)]
                self._backtrack(r, c, "", visited)

        print(f"Dictionary: {sorted(self.dictionary)}\n")
        print(f"Found {len(self.found)} word(s):")
        for w in sorted(self.found):
            print(f"  - {w}")

    def _backtrack(self, r: int, c: int, current: str, visited: list[list[bool]]) -> None:
        current += self.board[r][c]

        if len(current) >= 3 and current.lower() in self.dictionary:
            self.found.add(current)

        if not self._has_prefix(current.lower()):
            return

        visited[r][c] = True
        for dr, dc in _DIRS:
            nr, nc = r + dr, c + dc
            if 0 <= nr < self.rows and 0 <= nc < self.cols and not visited[nr][nc]:
                self._backtrack(nr, nc, current, visited)
        visited[r][c] = False

    def _has_prefix(self, prefix: str) -> bool:
        return any(w.startswith(prefix) for w in self.dictionary)

    def _print_board(self) -> None:
        print("Boggle Board:")
        for row in self.board:
            print("| " + " ".join(row) + " |")
        print()
