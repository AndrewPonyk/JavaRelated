"""Word Search — find words in a 2D letter grid (8 directions)."""

_DIRS = [(-1, -1), (-1, 0), (-1, 1), (0, -1), (0, 1), (1, -1), (1, 0), (1, 1)]


class WordSearch:
    def __init__(self, board: list[list[str]], words: list[str]):
        if not board:
            raise ValueError("Board must not be empty")
        self.board = board
        self.rows = len(board)
        self.cols = len(board[0])
        self.words = words
        self.found: set[str] = set()

    def solve_and_print(self) -> None:
        self._print_board()
        print(f"Searching for: {self.words}\n")

        for word in self.words:
            for r in range(self.rows):
                for c in range(self.cols):
                    visited: list[list[bool]] = [[False] * self.cols for _ in range(self.rows)]
                    self._backtrack(word, 0, r, c, visited)

        print(f"Found {len(self.found)} word(s):")
        for w in sorted(self.found):
            print(f"  - {w}")
        missing = [w for w in self.words if w not in self.found]
        if missing:
            print(f"Not found: {missing}")

    def _backtrack(self, word: str, idx: int, r: int, c: int, visited: list[list[bool]]) -> bool:
        if self.board[r][c] != word[idx]:
            return False
        if idx == len(word) - 1:
            self.found.add(word)
            return True
        visited[r][c] = True
        for dr, dc in _DIRS:
            nr, nc = r + dr, c + dc
            if 0 <= nr < self.rows and 0 <= nc < self.cols and not visited[nr][nc]:
                if self._backtrack(word, idx + 1, nr, nc, visited):
                    visited[r][c] = False
                    return True
        visited[r][c] = False
        return False

    def _print_board(self) -> None:
        print("Board:")
        for row in self.board:
            print("| " + " ".join(row) + " |")
        print()
