from __future__ import annotations


def bipartite_matching(graph: dict[str, list[str]]) -> dict[str, str]:
    match_right: dict[str, str] = {}

    def can_match(left: str, seen: set[str]) -> bool:
        for right in graph.get(left, []):
            if right in seen:
                continue
            seen.add(right)
            if right not in match_right or can_match(match_right[right], seen):
                match_right[right] = left
                return True
        return False

    for left in graph:
        can_match(left, set())
    return {left: right for right, left in match_right.items()}


if __name__ == "__main__":
    sample = {"u1": ["v1", "v2"], "u2": ["v1"], "u3": ["v2", "v3"]}
    print("Bipartite matching")
    print(bipartite_matching(sample))

