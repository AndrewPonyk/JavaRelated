def heap_sort(values: list[int]) -> list[int]:
    result = values[:]
    n = len(result)

    for i in range(n // 2 - 1, -1, -1):
        _heapify(result, n, i)

    for end in range(n - 1, 0, -1):
        result[0], result[end] = result[end], result[0]
        _heapify(result, end, 0)

    return result


def _heapify(values: list[int], size: int, root: int) -> None:
    largest = root
    left = 2 * root + 1
    right = 2 * root + 2

    if left < size and values[left] > values[largest]:
        largest = left
    if right < size and values[right] > values[largest]:
        largest = right

    if largest != root:
        values[root], values[largest] = values[largest], values[root]
        _heapify(values, size, largest)


if __name__ == "__main__":
    sample = [42, 7, 19, 3, 7, 99, 1]
    print("HeapSort")
    print(f"input : {sample}")
    print(f"output: {heap_sort(sample)}")
