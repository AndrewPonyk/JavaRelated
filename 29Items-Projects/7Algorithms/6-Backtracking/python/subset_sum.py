"""Subset Sum — find all subsets that sum to a target value."""


class SubsetSum:
    def __init__(self, numbers: list[int], target: int):
        self.numbers = numbers
        self.target = target
        self.solutions: list[list[int]] = []

    def solve_and_print(self) -> None:
        print(f"Set: {self.numbers}")
        print(f"Target sum: {self.target}\n")

        self._backtrack(0, 0, [])

        if not self.solutions:
            print(f"No subset sums to {self.target}.")
        else:
            print(f"Found {len(self.solutions)} subset(s):")
            for i, sol in enumerate(self.solutions, 1):
                print(f"  #{i}: {sol} = {self.target}")

    def _backtrack(self, index: int, current_sum: int, current: list[int]) -> None:
        if current_sum == self.target and current:
            self.solutions.append(current[:])
        for i in range(index, len(self.numbers)):
            if current_sum + self.numbers[i] <= self.target:
                current.append(self.numbers[i])
                self._backtrack(i + 1, current_sum + self.numbers[i], current)
                current.pop()
