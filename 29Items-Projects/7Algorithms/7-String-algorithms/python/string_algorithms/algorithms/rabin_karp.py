BASE = 256
MOD = 1_000_000_007


def search(text: str, pattern: str) -> list[int]:
    if pattern == "":
        raise ValueError("pattern must not be empty")
    if len(pattern) > len(text):
        return []

    highest_base_power = pow(BASE, len(pattern) - 1, MOD)
    pattern_hash = 0
    window_hash = 0

    for index in range(len(pattern)):
        pattern_hash = (pattern_hash * BASE + ord(pattern[index])) % MOD
        window_hash = (window_hash * BASE + ord(text[index])) % MOD

    matches: list[int] = []
    for start in range(len(text) - len(pattern) + 1):
        if pattern_hash == window_hash and text[start : start + len(pattern)] == pattern:
            matches.append(start)

        if start < len(text) - len(pattern):
            window_hash = (
                window_hash - ord(text[start]) * highest_base_power
            ) % MOD
            window_hash = (
                window_hash * BASE + ord(text[start + len(pattern)])
            ) % MOD

    return matches
