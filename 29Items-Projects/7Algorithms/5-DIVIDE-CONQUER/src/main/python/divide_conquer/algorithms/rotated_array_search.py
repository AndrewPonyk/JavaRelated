"""Search in a rotated sorted array."""


def index_of(values: list[int], target: int) -> int:
    """Return target index or -1 when absent."""
    if values is None:
        raise ValueError("values must not be None")
    return _search(values, target, 0, len(values) - 1)


def _search(values: list[int], target: int, left: int, right: int) -> int:
    if left > right:
        return -1
    mid = left + (right - left) // 2
    if values[mid] == target:
        return mid
    if values[left] <= values[mid]:
        if values[left] <= target < values[mid]:
            return _search(values, target, left, mid - 1)
        return _search(values, target, mid + 1, right)
    if values[mid] < target <= values[right]:
        return _search(values, target, mid + 1, right)
    return _search(values, target, left, mid - 1)

