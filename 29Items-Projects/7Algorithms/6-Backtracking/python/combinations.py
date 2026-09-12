"""Combinations — generate all k-sized subsets from a set."""


class Combinations:
    def __init__(self, elements: list[int], k: int):
        self.elements = elements
        self.k = k
        self.results: list[list[int]] = []

    def solve_and_print(self) -> None:
        print(f"Set: {self.elements}")
        print(f"Combination size k={self.k}\n")

        self._backtrack(0, [])

        print(f"Found {len(self.results)} combination(s):")
        for i, combo in enumerate(self.results, 1):
            print(f"  #{i}: {combo}")

    def _backtrack(self, start: int, current: list[int]) -> None:
        if len(current) == self.k:
            self.results.append(current[:])
            return
        for i in range(start, len(self.elements)):
            current.append(self.elements[i])
            self._backtrack(i + 1, current)
            current.pop()
