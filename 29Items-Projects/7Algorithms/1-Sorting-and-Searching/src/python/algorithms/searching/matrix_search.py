def staircase_matrix_search(matrix: list[list[int]], target: int) -> tuple[int, int] | None:
    if not matrix or not matrix[0]:
        return None

    row = 0
    col = len(matrix[0]) - 1
    while row < len(matrix) and col >= 0:
        value = matrix[row][col]
        if value == target:
            return row, col
        if value > target:
            col -= 1
        else:
            row += 1
    return None


def flattened_binary_matrix_search(matrix: list[list[int]], target: int) -> tuple[int, int] | None:
    if not matrix or not matrix[0]:
        return None

    rows = len(matrix)
    cols = len(matrix[0])
    left = 0
    right = rows * cols - 1

    while left <= right:
        mid = (left + right) // 2
        row, col = divmod(mid, cols)
        value = matrix[row][col]
        if value == target:
            return row, col
        if value < target:
            left = mid + 1
        else:
            right = mid - 1
    return None


if __name__ == "__main__":
    row_col_sorted = [
        [1, 4, 7, 11],
        [2, 5, 8, 12],
        [3, 6, 9, 16],
    ]
    flattened_sorted = [[1, 3, 5], [7, 9, 11]]
    print("2D Matrix Search")
    print(f"staircase input : {row_col_sorted}")
    print("staircase target: 9")
    print(f"staircase output: {staircase_matrix_search(row_col_sorted, 9)}")
    print(f"flattened input : {flattened_sorted}")
    print("flattened target: 9")
    print(f"flattened output: {flattened_binary_matrix_search(flattened_sorted, 9)}")
