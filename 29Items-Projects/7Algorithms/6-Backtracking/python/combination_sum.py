"""Combination Sum — find all unique combinations that sum to target (unlimited reuse)."""


class CombinationSum:
    def __init__(self, candidates: list[int], target: int):
        if not candidates:
            raise ValueError("Candidates must not be empty")
        self.candidates = sorted(candidates)
        self.target = target
        self.solutions: list[list[int]] = []

    def solve_and_print(self) -> None:
        self._backtrack([], 0, 0)
        print(f"Found {len(self.solutions)} combination(s) summing to {self.target}\n")

        limit = min(len(self.solutions), 8)
        for i, sol in enumerate(self.solutions[:limit], 1):
            print(f"#{i}: {sol} = {sum(sol)}")
        if len(self.solutions) > limit:
            print(f"... and {len(self.solutions) - limit} more.")

    def _backtrack(self, current: list[int], start: int, total: int) -> None:
        if total == self.target:
            self.solutions.append(current[:])
            return
        for i in range(start, len(self.candidates)):
            if total + self.candidates[i] > self.target:
                break
            current.append(self.candidates[i])
            self._backtrack(current, i, total + self.candidates[i])
            current.pop()
