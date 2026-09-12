"""Karatsuba-style polynomial multiplication."""


def multiply(first: list[int], second: list[int]) -> list[int]:
    """Multiply coefficient arrays ordered from lowest to highest degree."""
    if not first or not second:
        raise ValueError("polynomials must not be empty")
    result_size = len(first) + len(second) - 1
    size = _next_power_of_two(max(len(first), len(second)))
    padded_first = first + [0] * (size - len(first))
    padded_second = second + [0] * (size - len(second))
    return _multiply_equal_size(padded_first, padded_second)[:result_size]


def _multiply_equal_size(first: list[int], second: list[int]) -> list[int]:
    if len(first) == 1:
        return [first[0] * second[0]]

    half = len(first) // 2
    low_first = first[:half]
    high_first = first[half:]
    low_second = second[:half]
    high_second = second[half:]

    z0 = _multiply_equal_size(low_first, low_second)
    z2 = _multiply_equal_size(high_first, high_second)
    z1 = _subtract(
        _subtract(_multiply_equal_size(_add(low_first, high_first), _add(low_second, high_second)), z0),
        z2,
    )

    result = [0] * (2 * len(first) - 1)
    _add_into(result, z0, 0)
    _add_into(result, z1, half)
    _add_into(result, z2, 2 * half)
    return result


def _add(first: list[int], second: list[int]) -> list[int]:
    return [a + b for a, b in zip(first, second, strict=True)]


def _subtract(first: list[int], second: list[int]) -> list[int]:
    result = first + [0] * max(0, len(second) - len(first))
    for index, value in enumerate(second):
        result[index] -= value
    return result


def _add_into(target: list[int], values: list[int], offset: int) -> None:
    for index, value in enumerate(values):
        target[offset + index] += value


def _next_power_of_two(value: int) -> int:
    result = 1
    while result < value:
        result *= 2
    return result

