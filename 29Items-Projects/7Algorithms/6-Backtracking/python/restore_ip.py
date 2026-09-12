"""Restore IP Addresses — find all valid IP address splits from a digit string."""


class RestoreIpAddresses:
    def __init__(self, s: str):
        if not s:
            raise ValueError("String must not be empty")
        self.s = s
        self.solutions: list[str] = []

    def solve_and_print(self) -> None:
        self._backtrack([], 0)
        print(f"Found {len(self.solutions)} valid IP address(es) for \"{self.s}\"\n")

        for i, sol in enumerate(self.solutions, 1):
            print(f"#{i}: {sol}")

    def _backtrack(self, segments: list[str], start: int) -> None:
        if len(segments) == 4:
            if start == len(self.s):
                self.solutions.append(".".join(segments))
            return
        for length in range(1, 4):
            if start + length > len(self.s):
                break
            seg = self.s[start : start + length]
            if self._valid(seg):
                segments.append(seg)
                self._backtrack(segments, start + length)
                segments.pop()

    def _valid(self, seg: str) -> bool:
        if len(seg) > 1 and seg[0] == "0":
            return False
        return int(seg) <= 255
