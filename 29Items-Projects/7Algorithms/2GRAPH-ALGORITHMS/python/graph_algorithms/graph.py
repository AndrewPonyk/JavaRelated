from __future__ import annotations

from collections import defaultdict
from dataclasses import dataclass, field


@dataclass
class Graph:
    directed: bool = False
    adjacency: dict[str, list[tuple[str, int]]] = field(default_factory=lambda: defaultdict(list))

    def add_edge(self, source: str, target: str, weight: int = 1) -> None:
        self.adjacency[source].append((target, weight))
        self.adjacency.setdefault(target, [])
        if not self.directed:
            self.adjacency[target].append((source, weight))

    def vertices(self) -> list[str]:
        return list(self.adjacency.keys())

    def edges(self) -> list[tuple[str, str, int]]:
        result: list[tuple[str, str, int]] = []
        seen: set[tuple[str, str]] = set()
        for source, neighbors in self.adjacency.items():
            for target, weight in neighbors:
                if self.directed or (target, source) not in seen:
                    result.append((source, target, weight))
                    seen.add((source, target))
        return result

