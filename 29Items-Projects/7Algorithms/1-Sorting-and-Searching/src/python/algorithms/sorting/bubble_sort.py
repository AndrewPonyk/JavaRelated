def bubble_sort(values: list[int]) -> list[int]:
    result = values[:]
    n = len(result)

    for i in range(n):
        swapped = False
        for j in range(0, n - i - 1):
            if result[j] > result[j + 1]:
                result[j], result[j + 1] = result[j + 1], result[j]
                swapped = True
        if not swapped:
            break

    return result


if __name__ == "__main__":
    sample = [42, 7, 19, 3, 7, 99, 1]
    print("BubbleSort")
    print(f"input : {sample}")
    print(f"output: {bubble_sort(sample)}")
