"""Palindrome Partitioning — split string into substrings where each is a palindrome."""


class PalindromePartitioning:
    def __init__(self, s: str):
        if not s:
            raise ValueError("String must not be empty")
        self.s = s
        self.solutions: list[list[str]] = []

    def solve_and_print(self) -> None:
        self._backtrack([], 0)
        print(f"Found {len(self.solutions)} partition(s) for \"{self.s}\"\n")

        limit = min(len(self.solutions), 6)
        for i, sol in enumerate(self.solutions[:limit], 1):
            print(f"#{i}: {sol}")
        if len(self.solutions) > limit:
            print(f"... and {len(self.solutions) - limit} more.")

    def _backtrack(self, current: list[str], start: int) -> None:
        if start == len(self.s):
            self.solutions.append(current[:])
            return
        for end in range(start + 1, len(self.s) + 1):
            sub = self.s[start:end]
            if sub == sub[::-1]:
                current.append(sub)
                self._backtrack(current, end)
                current.pop()
