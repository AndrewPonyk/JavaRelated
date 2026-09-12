"""Cryptarithmetic — solve puzzles like SEND + MORE = MONEY."""


class Cryptarithmetic:
    def __init__(self, word1: str, word2: str, result: str):
        self.word1 = word1.upper()
        self.word2 = word2.upper()
        self.result = result.upper()

        seen: set[str] = set()
        self.letters: list[str] = []
        for ch in self.word1 + self.word2 + self.result:
            if ch not in seen:
                seen.add(ch)
                self.letters.append(ch)

        self.assignment: dict[str, int] = {}
        self.solutions: list[dict[str, int]] = []

    def solve_and_print(self) -> None:
        print(f"Puzzle: {self.word1} + {self.word2} = {self.result}")
        print(f"Unique letters: {self.letters}\n")

        self._backtrack(0, set())
        if not self.solutions:
            print("No solution found.")
        else:
            print(f"Found {len(self.solutions)} solution(s):\n")
            for i, sol in enumerate(self.solutions, 1):
                print(f"Solution #{i}:")
                print(f"  {self._format(self.word1, sol)}")
                print(f"+ {self._format(self.word2, sol)}")
                print(f"= {self._format(self.result, sol)}\n")

    def _backtrack(self, idx: int, used: set[int]) -> None:
        if idx == len(self.letters):
            if self._check():
                self.solutions.append(dict(self.assignment))
            return

        letter = self.letters[idx]
        is_leading = letter in (self.word1[0], self.word2[0], self.result[0])

        for digit in range(1 if is_leading else 0, 10):
            if digit not in used:
                self.assignment[letter] = digit
                used.add(digit)
                self._backtrack(idx + 1, used)
                del self.assignment[letter]
                used.discard(digit)

    def _check(self) -> bool:
        v1 = self._to_number(self.word1)
        v2 = self._to_number(self.word2)
        vr = self._to_number(self.result)
        return v1 + v2 == vr

    def _to_number(self, word: str) -> int:
        num = 0
        for ch in word:
            num = num * 10 + self.assignment[ch]
        return num

    def _format(self, word: str, sol: dict[str, int]) -> str:
        return "".join(str(sol[ch]) for ch in word)
