"""One-dimensional peak finding."""


def peak_index(values: list[int]) -> int:
    """Return an index whose value is not smaller than its neighbors."""
    if not values:
        raise ValueError("values must not be empty")
    return _find(values, 0, len(values) - 1)


def _find(values: list[int], left: int, right: int) -> int:
    if left == right:
        return left
    mid = left + (right - left) // 2
    if values[mid] < values[mid + 1]:
        return _find(values, mid + 1, right)
    return _find(values, left, mid)

