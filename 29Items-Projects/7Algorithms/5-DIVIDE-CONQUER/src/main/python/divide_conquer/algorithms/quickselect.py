"""Average-case linear-time selection using partitioning."""


def kth_smallest(values: list[int], k: int) -> int:
    """Return the zero-based kth smallest value without mutating the caller's list."""
    if not values:
        raise ValueError("values must not be empty")
    if k < 0 or k >= len(values):
        raise ValueError("k must be inside the list bounds")

    items = list(values)
    left = 0
    right = len(items) - 1

    while left <= right:
        pivot_index = _partition(items, left, right)
        if pivot_index == k:
            return items[pivot_index]
        if pivot_index < k:
            left = pivot_index + 1
        else:
            right = pivot_index - 1

    raise RuntimeError("selection failed")


def _partition(values: list[int], left: int, right: int) -> int:
    pivot = values[right]
    store = left
    for index in range(left, right):
        if values[index] <= pivot:
            values[store], values[index] = values[index], values[store]
            store += 1
    values[store], values[right] = values[right], values[store]
    return store

