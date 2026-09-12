def counting_sort(values: list[int]) -> list[int]:
    if not values:
        return []

    min_value = min(values)
    max_value = max(values)
    counts = [0] * (max_value - min_value + 1)

    for value in values:
        counts[value - min_value] += 1

    result: list[int] = []
    for offset, count in enumerate(counts):
        result.extend([offset + min_value] * count)
    return result


if __name__ == "__main__":
    sample = [42, 7, 19, 3, 7, 99, 1]
    print("CountingSort")
    print(f"input : {sample}")
    print(f"output: {counting_sort(sample)}")
