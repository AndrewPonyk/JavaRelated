def longest_common_substring(first: str, second: str) -> str:
    lengths = [[0] * (len(second) + 1) for _ in range(len(first) + 1)]
    best_length = 0
    best_end = 0

    for i, first_char in enumerate(first, start=1):
        for j, second_char in enumerate(second, start=1):
            if first_char == second_char:
                lengths[i][j] = lengths[i - 1][j - 1] + 1
                if lengths[i][j] > best_length:
                    best_length = lengths[i][j]
                    best_end = i

    return first[best_end - best_length : best_end]


def longest_common_substring_length(first: str, second: str) -> int:
    return len(longest_common_substring(first, second))
