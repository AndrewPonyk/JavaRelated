"""Permutations — generate all orderings of a set of elements."""


class Permutations:
    def __init__(self, elements: list[int]):
        self.arr = elements[:]
        self.results: list[list[int]] = []

    def solve_and_print(self) -> None:
        print(f"Elements: {self.arr}\n")

        self._backtrack(0)

        print(f"Found {len(self.results)} permutation(s):")
        for i, perm in enumerate(self.results, 1):
            print(f"  #{i}: {perm}")

    def _backtrack(self, start: int) -> None:
        if start == len(self.arr):
            self.results.append(self.arr[:])
            return
        for i in range(start, len(self.arr)):
            self.arr[start], self.arr[i] = self.arr[i], self.arr[start]
            self._backtrack(start + 1)
            self.arr[start], self.arr[i] = self.arr[i], self.arr[start]
