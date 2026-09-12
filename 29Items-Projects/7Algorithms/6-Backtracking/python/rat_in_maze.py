"""Rat in a Maze — find path through a maze grid from (0,0) to (n-1,n-1)."""

_DIRS = [(0, 1), (1, 0), (0, -1), (-1, 0)]  # right, down, left, up


class RatInMaze:
    def __init__(self, maze: list[list[int]]):
        if not maze:
            raise ValueError("Maze must not be empty")
        self.maze = maze
        self.n = len(maze)
        self.solutions: list[list[list[int]]] = []

    def solve_and_print(self) -> None:
        path = [[0] * self.n for _ in range(self.n)]
        if self.maze[0][0] == 1:
            path[0][0] = 1
            self._backtrack(path, 0, 0)

        print(f"Found {len(self.solutions)} path(s) in {self.n}x{self.n} maze\n")

        limit = min(len(self.solutions), 4)
        for i, sol in enumerate(self.solutions[:limit], 1):
            print(f"Path #{i}:")
            self._print_path(sol)
            print()
        if len(self.solutions) > limit:
            print(f"... and {len(self.solutions) - limit} more.")

    def _backtrack(self, path: list[list[int]], row: int, col: int) -> None:
        if row == self.n - 1 and col == self.n - 1:
            self.solutions.append([r[:] for r in path])
            return
        for dr, dc in _DIRS:
            nr, nc = row + dr, col + dc
            if self._is_safe(path, nr, nc):
                path[nr][nc] = 1
                self._backtrack(path, nr, nc)
                path[nr][nc] = 0

    def _is_safe(self, path: list[list[int]], r: int, c: int) -> bool:
        return 0 <= r < self.n and 0 <= c < self.n and self.maze[r][c] == 1 and path[r][c] == 0

    def _print_path(self, path: list[list[int]]) -> None:
        for r in range(self.n):
            row = "|"
            for c in range(self.n):
                if self.maze[r][c] == 0:
                    row += " # "
                elif path[r][c] == 1:
                    row += " * "
                else:
                    row += " . "
            row += "|"
            print(row)
