from math import sqrt


def jump_search(values: list[int], target: int) -> int:
    n = len(values)
    if n == 0:
        return -1

    step = max(1, int(sqrt(n)))
    previous = 0

    while previous < n and values[min(step, n) - 1] < target:
        previous = step
        step += max(1, int(sqrt(n)))
        if previous >= n:
            return -1

    for index in range(previous, min(step, n)):
        if values[index] == target:
            return index

    return -1


if __name__ == "__main__":
    sample = [1, 3, 7, 7, 19, 42, 99]
    target = 19
    print("Jump Search")
    print(f"input : {sample}")
    print(f"target: {target}")
    print(f"index : {jump_search(sample, target)}")
