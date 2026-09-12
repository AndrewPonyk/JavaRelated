def search(text: str, pattern: str) -> list[int]:
    if pattern == "":
        raise ValueError("pattern must not be empty")

    lps = _build_lps(pattern)
    matches: list[int] = []
    text_index = 0
    pattern_index = 0

    while text_index < len(text):
        if text[text_index] == pattern[pattern_index]:
            text_index += 1
            pattern_index += 1

        if pattern_index == len(pattern):
            matches.append(text_index - pattern_index)
            pattern_index = lps[pattern_index - 1]
        elif text_index < len(text) and text[text_index] != pattern[pattern_index]:
            if pattern_index:
                pattern_index = lps[pattern_index - 1]
            else:
                text_index += 1

    return matches


def _build_lps(pattern: str) -> list[int]:
    lps = [0] * len(pattern)
    length = 0
    index = 1

    while index < len(pattern):
        if pattern[index] == pattern[length]:
            length += 1
            lps[index] = length
            index += 1
        elif length:
            length = lps[length - 1]
        else:
            index += 1

    return lps
