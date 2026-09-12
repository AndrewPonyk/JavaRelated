"""Selection from two sorted arrays."""


def kth_smallest(first: list[int], second: list[int], k: int) -> int:
    """Return the zero-based kth smallest value from two sorted arrays."""
    if first is None or second is None or not first + second:
        raise ValueError("arrays must not both be empty")
    if k < 0 or k >= len(first) + len(second):
        raise ValueError("k must be inside combined bounds")
    return _kth(first, 0, second, 0, k + 1)


def _kth(first: list[int], first_start: int, second: list[int], second_start: int, k: int) -> int:
    if first_start >= len(first):
        return second[second_start + k - 1]
    if second_start >= len(second):
        return first[first_start + k - 1]
    if k == 1:
        return min(first[first_start], second[second_start])

    half = k // 2
    first_key_index = min(first_start + half, len(first)) - 1
    second_key_index = min(second_start + half, len(second)) - 1
    if first[first_key_index] <= second[second_key_index]:
        return _kth(first, first_key_index + 1, second, second_start, k - (first_key_index - first_start + 1))
    return _kth(first, first_start, second, second_key_index + 1, k - (second_key_index - second_start + 1))

