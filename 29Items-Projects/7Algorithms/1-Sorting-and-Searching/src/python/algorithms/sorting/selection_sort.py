def selection_sort(values: list[int]) -> list[int]:
    result = values[:]
    for i in range(len(result)):
        min_index = i
        for j in range(i + 1, len(result)):
            if result[j] < result[min_index]:
                min_index = j
        result[i], result[min_index] = result[min_index], result[i]
    return result


if __name__ == "__main__":
    sample = [42, 7, 19, 3, 7, 99, 1]
    print("SelectionSort")
    print(f"input : {sample}")
    print(f"output: {selection_sort(sample)}")
