def bucket_sort(values: list[float], bucket_count: int = 10) -> list[float]:
    if not values:
        return []
    if bucket_count <= 0:
        raise ValueError("bucket_count must be positive")

    min_value = min(values)
    max_value = max(values)
    if min_value == max_value:
        return values[:]

    buckets: list[list[float]] = [[] for _ in range(bucket_count)]
    value_range = max_value - min_value

    for value in values:
        index = int(((value - min_value) / value_range) * (bucket_count - 1))
        buckets[index].append(value)

    result: list[float] = []
    for bucket in buckets:
        result.extend(sorted(bucket))
    return result


if __name__ == "__main__":
    sample = [0.42, 0.07, 0.19, 0.03, 0.07, 0.99, 0.01]
    print("BucketSort")
    print(f"input : {sample}")
    print(f"output: {bucket_sort(sample)}")
