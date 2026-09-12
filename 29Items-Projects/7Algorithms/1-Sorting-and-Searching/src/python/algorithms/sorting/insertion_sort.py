def insertion_sort(values: list[int]) -> list[int]:
    result = values[:]
    for i in range(1, len(result)):
        key = result[i]
        j = i - 1
        while j >= 0 and result[j] > key:
            result[j + 1] = result[j]
            j -= 1
        result[j + 1] = key
    return result


if __name__ == "__main__":
    sample = [42, 7, 19, 3, 7, 99, 1]
    print("InsertionSort")
    print(f"input : {sample}")
    print(f"output: {insertion_sort(sample)}")
