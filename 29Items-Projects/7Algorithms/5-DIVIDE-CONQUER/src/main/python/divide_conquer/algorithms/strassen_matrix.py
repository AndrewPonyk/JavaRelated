"""Strassen matrix multiplication for square matrices."""

Matrix = list[list[int]]


def multiply(a: Matrix, b: Matrix) -> Matrix:
    """Multiply two square matrices, padding non-power-of-two sizes internally."""
    _validate(a, b)
    n = len(a)
    padded_size = _next_power_of_two(n)
    if padded_size != n:
        return _trim(_multiply_power_of_two(_pad(a, padded_size), _pad(b, padded_size)), n)
    return _multiply_power_of_two(a, b)


def _multiply_power_of_two(a: Matrix, b: Matrix) -> Matrix:
    n = len(a)
    if n == 1:
        return [[a[0][0] * b[0][0]]]

    half = n // 2
    a11 = _slice(a, 0, 0, half)
    a12 = _slice(a, 0, half, half)
    a21 = _slice(a, half, 0, half)
    a22 = _slice(a, half, half, half)
    b11 = _slice(b, 0, 0, half)
    b12 = _slice(b, 0, half, half)
    b21 = _slice(b, half, 0, half)
    b22 = _slice(b, half, half, half)

    p1 = _multiply_power_of_two(_add(a11, a22), _add(b11, b22))
    p2 = _multiply_power_of_two(_add(a21, a22), b11)
    p3 = _multiply_power_of_two(a11, _subtract(b12, b22))
    p4 = _multiply_power_of_two(a22, _subtract(b21, b11))
    p5 = _multiply_power_of_two(_add(a11, a12), b22)
    p6 = _multiply_power_of_two(_subtract(a21, a11), _add(b11, b12))
    p7 = _multiply_power_of_two(_subtract(a12, a22), _add(b21, b22))

    c11 = _add(_subtract(_add(p1, p4), p5), p7)
    c12 = _add(p3, p5)
    c21 = _add(p2, p4)
    c22 = _add(_subtract(_add(p1, p3), p2), p6)
    return _join(c11, c12, c21, c22)


def _validate(a: Matrix, b: Matrix) -> None:
    if a is None or b is None:
        raise ValueError("matrices must not be None")
    if not a or len(a) != len(b):
        raise ValueError("matrices must be non-empty and have the same square size")
    n = len(a)
    if any(row is None or len(row) != n for row in a) or any(row is None or len(row) != n for row in b):
        raise ValueError("matrices must be square")


def _next_power_of_two(value: int) -> int:
    result = 1
    while result < value:
        result *= 2
    return result


def _pad(matrix: Matrix, size: int) -> Matrix:
    result = [[0 for _ in range(size)] for _ in range(size)]
    for row_index, row in enumerate(matrix):
        result[row_index][: len(matrix)] = row
    return result


def _trim(matrix: Matrix, size: int) -> Matrix:
    return [row[:size] for row in matrix[:size]]


def _add(a: Matrix, b: Matrix) -> Matrix:
    return [[a[row][col] + b[row][col] for col in range(len(a))] for row in range(len(a))]


def _subtract(a: Matrix, b: Matrix) -> Matrix:
    return [[a[row][col] - b[row][col] for col in range(len(a))] for row in range(len(a))]


def _slice(matrix: Matrix, row: int, col: int, size: int) -> Matrix:
    return [matrix[index][col : col + size] for index in range(row, row + size)]


def _join(c11: Matrix, c12: Matrix, c21: Matrix, c22: Matrix) -> Matrix:
    top = [left + right for left, right in zip(c11, c12, strict=True)]
    bottom = [left + right for left, right in zip(c21, c22, strict=True)]
    return top + bottom
