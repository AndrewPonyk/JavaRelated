def longest_palindrome(text: str) -> str:
    if text == "":
        return ""

    transformed = "#" + "#".join(text) + "#"
    radii = [0] * len(transformed)
    center = 0
    right = 0
    best_center = 0
    best_radius = 0

    for index in range(len(transformed)):
        mirror = 2 * center - index
        if index < right:
            radii[index] = min(right - index, radii[mirror])

        while (
            index - radii[index] - 1 >= 0
            and index + radii[index] + 1 < len(transformed)
            and transformed[index - radii[index] - 1]
            == transformed[index + radii[index] + 1]
        ):
            radii[index] += 1

        if index + radii[index] > right:
            center = index
            right = index + radii[index]

        if radii[index] > best_radius:
            best_radius = radii[index]
            best_center = index

    start = (best_center - best_radius) // 2
    return text[start : start + best_radius]
