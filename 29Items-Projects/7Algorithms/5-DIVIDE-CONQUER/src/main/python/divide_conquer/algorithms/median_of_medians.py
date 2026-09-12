"""Deterministic worst-case linear-time selection."""


def kth_smallest(values: list[int], k: int) -> int:
    """Return the zero-based kth smallest value using median-of-medians pivots."""
    if not values:
        raise ValueError("values must not be empty")
    if any(value is None for value in values):
        raise ValueError("values must not contain None values")
    if k < 0 or k >= len(values):
        raise ValueError("k must be inside the list bounds")
    return _select(list(values), k)


def _select(values: list[int], k: int) -> int:
    if len(values) <= 5:
        return sorted(values)[k]

    medians = []
    for index in range(0, len(values), 5):
        group = sorted(values[index : index + 5])
        medians.append(group[len(group) // 2])

    pivot = _select(medians, len(medians) // 2)
    lows = [value for value in values if value < pivot]
    equals = [value for value in values if value == pivot]
    highs = [value for value in values if value > pivot]

    if k < len(lows):
        return _select(lows, k)
    if k < len(lows) + len(equals):
        return pivot
    return _select(highs, k - len(lows) - len(equals))
