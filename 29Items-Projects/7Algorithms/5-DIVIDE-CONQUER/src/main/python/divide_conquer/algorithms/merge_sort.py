"""Merge sort."""


def sort(values: list[int]) -> list[int]:
    """Return a sorted copy of the input list."""
    if values is None:
        raise ValueError("values must not be None")
    if len(values) <= 1:
        return list(values)

    mid = len(values) // 2
    left = sort(values[:mid])
    right = sort(values[mid:])
    return _merge(left, right)


def _merge(left: list[int], right: list[int]) -> list[int]:
    result: list[int] = []
    left_index = 0
    right_index = 0

    while left_index < len(left) and right_index < len(right):
        if left[left_index] <= right[right_index]:
            result.append(left[left_index])
            left_index += 1
        else:
            result.append(right[right_index])
            right_index += 1

    result.extend(left[left_index:])
    result.extend(right[right_index:])
    return result

