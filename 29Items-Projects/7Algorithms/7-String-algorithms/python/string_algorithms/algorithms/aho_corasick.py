from __future__ import annotations

from collections import deque
from dataclasses import dataclass, field


@dataclass(frozen=True)
class MatchResult:
    algorithm: str
    pattern: str
    start_index: int


@dataclass
class _Node:
    children: dict[str, "_Node"] = field(default_factory=dict)
    outputs: list[int] = field(default_factory=list)
    failure: "_Node | None" = None


class AhoCorasick:
    def __init__(self, patterns: list[str]) -> None:
        if not patterns or any(pattern == "" for pattern in patterns):
            raise ValueError("patterns must contain at least one non-empty value")
        self._patterns = list(patterns)
        self._root = _Node()
        self._build_trie()
        self._build_failure_links()

    def search(self, text: str) -> list[MatchResult]:
        matches: list[MatchResult] = []
        current = self._root

        for index, char in enumerate(text):
            while current is not self._root and char not in current.children:
                current = current.failure or self._root

            current = current.children.get(char, self._root)

            for pattern_index in current.outputs:
                pattern = self._patterns[pattern_index]
                matches.append(
                    MatchResult("Aho-Corasick", pattern, index - len(pattern) + 1)
                )

        return matches

    def _build_trie(self) -> None:
        for pattern_index, pattern in enumerate(self._patterns):
            current = self._root
            for char in pattern:
                current = current.children.setdefault(char, _Node())
            current.outputs.append(pattern_index)

    def _build_failure_links(self) -> None:
        self._root.failure = self._root
        queue: deque[_Node] = deque()

        for child in self._root.children.values():
            child.failure = self._root
            queue.append(child)

        while queue:
            current = queue.popleft()
            for char, child in current.children.items():
                fallback = current.failure or self._root
                while fallback is not self._root and char not in fallback.children:
                    fallback = fallback.failure or self._root

                child.failure = fallback.children.get(char, self._root)
                child.outputs.extend(child.failure.outputs)
                queue.append(child)
