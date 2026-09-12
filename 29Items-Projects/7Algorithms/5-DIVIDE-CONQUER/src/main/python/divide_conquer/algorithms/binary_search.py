"""Recursive binary search."""


def index_of(sorted_values: list[int], target: int) -> int:
    """Return the target index in a sorted list, or -1 when absent."""
    if sorted_values is None:
        raise ValueError("values must not be None")
    return _search(sorted_values, target, 0, len(sorted_values) - 1)


def _search(values: list[int], target: int, left: int, right: int) -> int:
    if left > right:
        return -1

    mid = left + (right - left) // 2
    if values[mid] == target:
        return mid
    if target < values[mid]:
        return _search(values, target, left, mid - 1)
    return _search(values, target, mid + 1, right)

