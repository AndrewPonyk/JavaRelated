from __future__ import annotations

from dataclasses import dataclass, field


@dataclass
class _Node:
    children: dict[str, "_Node"] = field(default_factory=dict)
    word: bool = False


class Trie:
    def __init__(self) -> None:
        self._root = _Node()

    def insert(self, word: str) -> None:
        current = self._root
        for char in word:
            current = current.children.setdefault(char, _Node())
        current.word = True

    def contains(self, word: str) -> bool:
        node = self._find_node(word)
        return node is not None and node.word

    def starts_with(self, prefix: str) -> bool:
        return self._find_node(prefix) is not None

    def _find_node(self, value: str) -> _Node | None:
        current = self._root
        for char in value:
            current = current.children.get(char)
            if current is None:
                return None
        return current
