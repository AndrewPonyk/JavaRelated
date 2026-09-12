def radix_sort(values: list[int]) -> list[int]:
    if any(value < 0 for value in values):
        raise ValueError("radix_sort supports non-negative integers only")

    result = values[:]
    if not result:
        return result

    exponent = 1
    max_value = max(result)
    while max_value // exponent > 0:
        result = _counting_by_digit(result, exponent)
        exponent *= 10
    return result


def _counting_by_digit(values: list[int], exponent: int) -> list[int]:
    output = [0] * len(values)
    counts = [0] * 10

    for value in values:
        counts[(value // exponent) % 10] += 1

    for i in range(1, 10):
        counts[i] += counts[i - 1]

    for value in reversed(values):
        digit = (value // exponent) % 10
        output[counts[digit] - 1] = value
        counts[digit] -= 1

    return output


if __name__ == "__main__":
    sample = [42, 7, 19, 3, 7, 99, 1]
    print("RadixSort")
    print(f"input : {sample}")
    print(f"output: {radix_sort(sample)}")
