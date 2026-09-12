def shell_sort(values: list[int]) -> list[int]:
    result = values[:]
    gap = len(result) // 2

    while gap > 0:
        for i in range(gap, len(result)):
            current = result[i]
            j = i
            while j >= gap and result[j - gap] > current:
                result[j] = result[j - gap]
                j -= gap
            result[j] = current
        gap //= 2

    return result


if __name__ == "__main__":
    sample = [42, 7, 19, 3, 7, 99, 1]
    print("ShellSort")
    print(f"input : {sample}")
    print(f"output: {shell_sort(sample)}")
