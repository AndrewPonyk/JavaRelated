"""Count inversions with a merge-sort style combine step."""


def count(values: list[int]) -> int:
    """Return the number of pairs i < j where values[i] > values[j]."""
    if values is None:
        raise ValueError("values must not be None")
    _, inversions = _sort_and_count(list(values))
    return inversions


def _sort_and_count(values: list[int]) -> tuple[list[int], int]:
    if len(values) <= 1:
        return values, 0

    mid = len(values) // 2
    left, left_count = _sort_and_count(values[:mid])
    right, right_count = _sort_and_count(values[mid:])
    merged, split_count = _merge_and_count(left, right)
    return merged, left_count + right_count + split_count


def _merge_and_count(left: list[int], right: list[int]) -> tuple[list[int], int]:
    result: list[int] = []
    left_index = 0
    right_index = 0
    inversions = 0

    while left_index < len(left) and right_index < len(right):
        if left[left_index] <= right[right_index]:
            result.append(left[left_index])
            left_index += 1
        else:
            result.append(right[right_index])
            right_index += 1
            inversions += len(left) - left_index

    result.extend(left[left_index:])
    result.extend(right[right_index:])
    return result, inversions

