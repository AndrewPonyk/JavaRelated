"""Tug of War — partition set into two subsets with minimum absolute difference."""


class TugOfWar:
    def __init__(self, arr: list[int]):
        if len(arr) < 2:
            raise ValueError("Array must have at least 2 elements")
        self.arr = arr
        self.n = len(arr)
        self.min_diff = float("inf")
        self.best_set1: list[int] = []
        self.best_set2: list[int] = []

    def solve_and_print(self) -> None:
        print(f"Input: {self.arr}")
        self._backtrack([], [], 0)
        print(f"Minimum difference: {self.min_diff}\n")
        print(f"Set 1: {self.best_set1} (sum = {sum(self.best_set1)})")
        print(f"Set 2: {self.best_set2} (sum = {sum(self.best_set2)})")

    def _backtrack(self, set1: list[int], set2: list[int], idx: int) -> None:
        if idx == self.n:
            diff = abs(sum(set1) - sum(set2))
            if diff < self.min_diff:
                self.min_diff = diff
                self.best_set1 = set1[:]
                self.best_set2 = set2[:]
            return

        if abs(sum(set1) - sum(set2)) > self.min_diff:
            return

        set1.append(self.arr[idx])
        self._backtrack(set1, set2, idx + 1)
        set1.pop()

        set2.append(self.arr[idx])
        self._backtrack(set1, set2, idx + 1)
        set2.pop()
