"""Educational Toom-Cook 3-way integer multiplication."""


def multiply(x: int, y: int) -> int:
    """Multiply two integers by splitting each value into three decimal parts."""
    if x < 0 or y < 0:
        sign = -1 if (x < 0) ^ (y < 0) else 1
        return sign * multiply(abs(x), abs(y))
    if x.bit_length() < 32 or y.bit_length() < 32:
        return x * y

    digits = max(len(str(x)), len(str(y)))
    part_size = (digits + 2) // 3
    base = 10**part_size
    base_squared = base * base

    a0 = x % base
    a1 = (x // base) % base
    a2 = x // base_squared
    b0 = y % base
    b1 = (y // base) % base
    b2 = y // base_squared

    p0 = multiply(a0, b0)
    p1 = multiply(a0 + a1 + a2, b0 + b1 + b2)
    pm1 = multiply(a0 - a1 + a2, b0 - b1 + b2)
    p2 = multiply(a0 + 2 * a1 + 4 * a2, b0 + 2 * b1 + 4 * b2)
    pinf = multiply(a2, b2)

    c0 = p0
    c4 = pinf
    s1 = p1 - c0 - c4
    sm1 = pm1 - c0 - c4
    s2 = p2 - c0 - 16 * c4
    c2 = (s1 + sm1) // 2
    c1_plus_c3 = (s1 - sm1) // 2
    c3 = (s2 - 4 * c2 - 2 * c1_plus_c3) // 6
    c1 = c1_plus_c3 - c3

    return c0 + c1 * base + c2 * base**2 + c3 * base**3 + c4 * base**4
