"""Recursive Cooley-Tukey FFT."""

from __future__ import annotations

from cmath import exp, pi


def fft(values: list[complex]) -> list[complex]:
    """Return the discrete Fourier transform for power-of-two input length."""
    if not values or len(values) & (len(values) - 1):
        raise ValueError("input length must be a non-empty power of two")
    if any(value is None for value in values):
        raise ValueError("input must not contain None values")
    if len(values) == 1:
        return list(values)

    even = fft(values[0::2])
    odd = fft(values[1::2])
    result = [0j] * len(values)

    half = len(values) // 2
    for index in range(half):
        twiddle = exp(-2j * pi * index / len(values)) * odd[index]
        result[index] = even[index] + twiddle
        result[index + half] = even[index] - twiddle
    return result


def inverse_fft(values: list[complex]) -> list[complex]:
    """Return the inverse transform using conjugation and scaling."""
    if not values or len(values) & (len(values) - 1):
        raise ValueError("input length must be a non-empty power of two")
    if any(value is None for value in values):
        raise ValueError("input must not contain None values")
    conjugated = [value.conjugate() for value in values]
    transformed = fft(conjugated)
    return [value.conjugate() / len(values) for value in transformed]


def convolution(first: list[float], second: list[float]) -> list[float]:
    """Convolve two real-valued signals with FFT multiplication."""
    if not first or not second:
        raise ValueError("signals must not be empty")
    result_size = len(first) + len(second) - 1
    size = 1
    while size < result_size:
        size *= 2
    padded_first = [complex(value, 0) for value in first] + [0j] * (size - len(first))
    padded_second = [complex(value, 0) for value in second] + [0j] * (size - len(second))
    transformed_first = fft(padded_first)
    transformed_second = fft(padded_second)
    product = [a * b for a, b in zip(transformed_first, transformed_second, strict=True)]
    return [value.real for value in inverse_fft(product)[:result_size]]
