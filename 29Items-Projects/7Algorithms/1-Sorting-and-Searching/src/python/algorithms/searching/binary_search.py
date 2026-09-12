def binary_search(values: list[int], target: int) -> int:
    left = 0
    right = len(values) - 1

    while left <= right:
        mid = (left + right) // 2
        if values[mid] == target:
            return mid
        if values[mid] < target:
            left = mid + 1
        else:
            right = mid - 1

    return -1


if __name__ == "__main__":
    sample = [1, 3, 7, 7, 19, 42, 99]
    target = 19
    print("Binary Search")
    print(f"input : {sample}")
    print(f"target: {target}")
    print(f"index : {binary_search(sample, target)}")
