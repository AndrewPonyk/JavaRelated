"""Karatsuba multiplication."""


def multiply(x: int, y: int) -> int:
    """Multiply two integers with Karatsuba's divide-and-conquer strategy."""
    if x < 0 or y < 0:
        sign = -1 if (x < 0) ^ (y < 0) else 1
        return sign * multiply(abs(x), abs(y))
    if x < 10 or y < 10:
        return x * y

    digits = max(len(str(x)), len(str(y)))
    half = digits // 2
    base = 10**half

    high_x, low_x = divmod(x, base)
    high_y, low_y = divmod(y, base)

    z0 = multiply(low_x, low_y)
    z2 = multiply(high_x, high_y)
    z1 = multiply(low_x + high_x, low_y + high_y) - z2 - z0

    return z2 * base * base + z1 * base + z0

