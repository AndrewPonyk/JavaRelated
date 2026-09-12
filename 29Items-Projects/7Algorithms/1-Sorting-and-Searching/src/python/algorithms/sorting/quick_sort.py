def quick_sort(values: list[int]) -> list[int]:
    if len(values) <= 1:
        return values[:]

    pivot = values[len(values) // 2]
    left = [value for value in values if value < pivot]
    middle = [value for value in values if value == pivot]
    right = [value for value in values if value > pivot]
    return quick_sort(left) + middle + quick_sort(right)


if __name__ == "__main__":
    sample = [42, 7, 19, 3, 7, 99, 1]
    print("QuickSort")
    print(f"input : {sample}")
    print(f"output: {quick_sort(sample)}")
