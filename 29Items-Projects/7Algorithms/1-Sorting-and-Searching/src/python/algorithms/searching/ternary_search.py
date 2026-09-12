def ternary_search(values: list[int], target: int) -> int:
    left = 0
    right = len(values) - 1

    while left <= right:
        third = (right - left) // 3
        mid1 = left + third
        mid2 = right - third

        if values[mid1] == target:
            return mid1
        if values[mid2] == target:
            return mid2
        if target < values[mid1]:
            right = mid1 - 1
        elif target > values[mid2]:
            left = mid2 + 1
        else:
            left = mid1 + 1
            right = mid2 - 1

    return -1


if __name__ == "__main__":
    sample = [1, 3, 7, 7, 19, 42, 99]
    target = 19
    print("Ternary Search")
    print(f"input : {sample}")
    print(f"target: {target}")
    print(f"index : {ternary_search(sample, target)}")
