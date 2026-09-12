"""Exponentiation by squaring."""


def pow_int(base: int, exponent: int) -> int:
    """Return base raised to a non-negative integer exponent."""
    if exponent < 0:
        raise ValueError("exponent must be non-negative")
    if exponent == 0:
        return 1

    half = pow_int(base, exponent // 2)
    squared = half * half
    return squared if exponent % 2 == 0 else squared * base

