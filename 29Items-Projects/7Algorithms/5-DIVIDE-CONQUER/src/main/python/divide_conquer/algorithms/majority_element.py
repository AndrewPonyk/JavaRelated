"""Majority element by recursive candidate comparison."""


def find(values: list[int]) -> int | None:
    """Return the value appearing more than n/2 times, or None."""
    if not values:
        raise ValueError("values must not be empty")
    candidate = _candidate(values, 0, len(values) - 1)
    return candidate if values.count(candidate) > len(values) // 2 else None


def _candidate(values: list[int], left: int, right: int) -> int:
    if left == right:
        return values[left]
    mid = left + (right - left) // 2
    left_candidate = _candidate(values, left, mid)
    right_candidate = _candidate(values, mid + 1, right)
    if left_candidate == right_candidate:
        return left_candidate
    left_count = sum(1 for index in range(left, right + 1) if values[index] == left_candidate)
    right_count = sum(1 for index in range(left, right + 1) if values[index] == right_candidate)
    return left_candidate if left_count >= right_count else right_candidate

