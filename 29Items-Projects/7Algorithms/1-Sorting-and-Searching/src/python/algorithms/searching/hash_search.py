def hash_search(values: list[int], target: int) -> int:
    index_by_value: dict[int, int] = {}
    for index, value in enumerate(values):
        index_by_value.setdefault(value, index)
    return index_by_value.get(target, -1)


if __name__ == "__main__":
    sample = [42, 7, 19, 3, 7, 99, 1]
    target = 19
    print("Hash-based Search")
    print(f"input : {sample}")
    print(f"target: {target}")
    print(f"index : {hash_search(sample, target)}")
