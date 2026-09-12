try:
    from algorithms.searching.binary_search import binary_search
except ModuleNotFoundError:
    from binary_search import binary_search


def exponential_search(values: list[int], target: int) -> int:
    if not values:
        return -1
    if values[0] == target:
        return 0

    bound = 1
    while bound < len(values) and values[bound] < target:
        bound *= 2

    offset = bound // 2
    local_index = binary_search(values[offset : min(bound + 1, len(values))], target)
    return -1 if local_index == -1 else offset + local_index


if __name__ == "__main__":
    sample = [1, 3, 7, 7, 19, 42, 99]
    target = 19
    print("Exponential Search")
    print(f"input : {sample}")
    print(f"target: {target}")
    print(f"index : {exponential_search(sample, target)}")
