def interpolation_search(values: list[int], target: int) -> int:
    low = 0
    high = len(values) - 1

    while low <= high and values and values[low] <= target <= values[high]:
        if values[high] == values[low]:
            return low if values[low] == target else -1

        position = low + ((target - values[low]) * (high - low)) // (values[high] - values[low])
        if values[position] == target:
            return position
        if values[position] < target:
            low = position + 1
        else:
            high = position - 1

    return -1


if __name__ == "__main__":
    sample = [1, 3, 7, 7, 19, 42, 99]
    target = 19
    print("Interpolation Search")
    print(f"input : {sample}")
    print(f"target: {target}")
    print(f"index : {interpolation_search(sample, target)}")
