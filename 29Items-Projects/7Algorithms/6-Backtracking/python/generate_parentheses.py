"""Generate Parentheses — all valid combinations of n pairs of parentheses."""


class GenerateParentheses:
    def __init__(self, n: int):
        if n <= 0:
            raise ValueError("n must be > 0")
        self.n = n
        self.solutions: list[str] = []

    def solve_and_print(self) -> None:
        self._backtrack("", 0, 0)
        print(f"Found {len(self.solutions)} valid combination(s) for n={self.n}\n")

        limit = min(len(self.solutions), 8)
        for i, sol in enumerate(self.solutions[:limit], 1):
            print(f"#{i}: {sol}")
        if len(self.solutions) > limit:
            print(f"... and {len(self.solutions) - limit} more.")

    def _backtrack(self, current: str, open_cnt: int, close_cnt: int) -> None:
        if len(current) == self.n * 2:
            self.solutions.append(current)
            return
        if open_cnt < self.n:
            self._backtrack(current + "(", open_cnt + 1, close_cnt)
        if close_cnt < open_cnt:
            self._backtrack(current + ")", open_cnt, close_cnt + 1)
