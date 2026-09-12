def longest_common_subsequence(first: str, second: str) -> str:
    lengths = [[0] * (len(second) + 1) for _ in range(len(first) + 1)]

    for i, first_char in enumerate(first, start=1):
        for j, second_char in enumerate(second, start=1):
            if first_char == second_char:
                lengths[i][j] = lengths[i - 1][j - 1] + 1
            else:
                lengths[i][j] = max(lengths[i - 1][j], lengths[i][j - 1])

    result: list[str] = []
    i = len(first)
    j = len(second)
    while i > 0 and j > 0:
        if first[i - 1] == second[j - 1]:
            result.append(first[i - 1])
            i -= 1
            j -= 1
        elif lengths[i - 1][j] >= lengths[i][j - 1]:
            i -= 1
        else:
            j -= 1

    return "".join(reversed(result))


def longest_common_subsequence_length(first: str, second: str) -> int:
    return len(longest_common_subsequence(first, second))
