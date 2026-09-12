"""Divide-and-conquer DP optimization for monotone split points."""

INF = 10**30


def min_squared_partition_cost(values: list[int], groups: int) -> int:
    """Partition values into groups minimizing sum of squared group sums."""
    if not values or groups <= 0:
        raise ValueError("values must not be empty and groups must be positive")
    prefix = [0]
    for value in values:
        prefix.append(prefix[-1] + value)

    previous = [INF] * (len(values) + 1)
    previous[0] = 0
    for _ in range(groups):
        current = [INF] * (len(values) + 1)
        _compute(current, previous, prefix, 1, len(values), 0, len(values) - 1)
        previous = current
    return previous[len(values)]


def _compute(
    current: list[int],
    previous: list[int],
    prefix: list[int],
    left: int,
    right: int,
    opt_left: int,
    opt_right: int,
) -> None:
    if left > right:
        return
    mid = left + (right - left) // 2
    best_split = opt_left
    best_cost = INF
    for split in range(opt_left, min(mid - 1, opt_right) + 1):
        candidate = previous[split] + _cost(prefix, split, mid)
        if candidate < best_cost:
            best_cost = candidate
            best_split = split
    current[mid] = best_cost
    _compute(current, previous, prefix, left, mid - 1, opt_left, best_split)
    _compute(current, previous, prefix, mid + 1, right, best_split, opt_right)


def _cost(prefix: list[int], start: int, end: int) -> int:
    total = prefix[end] - prefix[start]
    return total * total

