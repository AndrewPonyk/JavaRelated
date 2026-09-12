from __future__ import annotations

from dataclasses import dataclass, field


@dataclass
class _Node:
    children: dict[str, "_Edge"] = field(default_factory=dict)
    terminal: bool = False


@dataclass
class _Edge:
    label: str
    target: _Node


class SuffixTree:
    def __init__(self, text: str) -> None:
        self._root = _Node()
        for start in range(len(text)):
            self._insert(text[start:])

    def contains(self, pattern: str) -> bool:
        current = self._root
        pattern_index = 0

        while pattern_index < len(pattern):
            edge = current.children.get(pattern[pattern_index])
            if edge is None:
                return False

            edge_index = 0
            while edge_index < len(edge.label) and pattern_index < len(pattern):
                if edge.label[edge_index] != pattern[pattern_index]:
                    return False
                edge_index += 1
                pattern_index += 1

            if pattern_index == len(pattern):
                return True

            current = edge.target
        return True

    @property
    def edge_count(self) -> int:
        return self._count_edges(self._root)

    def _insert(self, suffix: str) -> None:
        current = self._root
        remaining = suffix

        while remaining:
            edge = current.children.get(remaining[0])
            if edge is None:
                leaf = _Node(terminal=True)
                current.children[remaining[0]] = _Edge(remaining, leaf)
                return

            common_length = _common_prefix_length(remaining, edge.label)
            if common_length == len(edge.label):
                current = edge.target
                remaining = remaining[common_length:]
                continue

            self._split_edge(current, edge, common_length, remaining)
            return

        current.terminal = True

    def _split_edge(
        self, parent: _Node, edge: _Edge, common_length: int, remaining: str
    ) -> None:
        common_prefix = edge.label[:common_length]
        old_remainder = edge.label[common_length:]
        intermediate = _Node()
        intermediate.children[old_remainder[0]] = _Edge(old_remainder, edge.target)

        edge.label = common_prefix
        edge.target = intermediate
        parent.children[common_prefix[0]] = edge

        new_remainder = remaining[common_length:]
        if new_remainder:
            leaf = _Node(terminal=True)
            intermediate.children[new_remainder[0]] = _Edge(new_remainder, leaf)
        else:
            intermediate.terminal = True

    def _count_edges(self, node: _Node) -> int:
        return len(node.children) + sum(
            self._count_edges(edge.target) for edge in node.children.values()
        )


def _common_prefix_length(left: str, right: str) -> int:
    index = 0
    limit = min(len(left), len(right))
    while index < limit and left[index] == right[index]:
        index += 1
    return index
