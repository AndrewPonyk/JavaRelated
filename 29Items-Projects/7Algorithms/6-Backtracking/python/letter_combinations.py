"""Letter Combinations — generate all T9-style letter combinations for a phone number."""

_MAP = ["", "", "abc", "def", "ghi", "jkl", "mno", "pqrs", "tuv", "wxyz"]


class LetterCombinations:
    def __init__(self, digits: str):
        if not digits:
            raise ValueError("Digits must not be empty")
        self.digits = digits
        self.solutions: list[str] = []

    def solve_and_print(self) -> None:
        self._backtrack("", 0)
        print(f"Found {len(self.solutions)} combination(s) for digits \"{self.digits}\"\n")

        limit = min(len(self.solutions), 8)
        for i, sol in enumerate(self.solutions[:limit], 1):
            print(f"#{i}: {sol}")
        if len(self.solutions) > limit:
            print(f"... and {len(self.solutions) - limit} more.")

    def _backtrack(self, current: str, index: int) -> None:
        if index == len(self.digits):
            self.solutions.append(current)
            return
        for ch in _MAP[int(self.digits[index])]:
            self._backtrack(current + ch, index + 1)
