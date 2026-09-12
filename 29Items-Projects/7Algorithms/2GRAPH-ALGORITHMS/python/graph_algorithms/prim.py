from __future__ import annotations

import heapq


def prim(graph: dict[str, list[tuple[str, int]]], start: str) -> tuple[int, list[tuple[str, str, int]]]:
    if start not in graph:
        raise ValueError(f"start vertex {start!r} is not in the graph")
    visited = {start}
    heap = [(weight, start, neighbor) for neighbor, weight in graph[start]]
    heapq.heapify(heap)
    total = 0
    tree: list[tuple[str, str, int]] = []

    while heap and len(visited) < len(graph):
        weight, source, target = heapq.heappop(heap)
        if target in visited:
            continue
        visited.add(target)
        total += weight
        tree.append((source, target, weight))
        for neighbor, next_weight in graph[target]:
            if neighbor not in graph:
                raise ValueError(f"edge references unknown vertex {neighbor!r}")
            if neighbor not in visited:
                heapq.heappush(heap, (next_weight, target, neighbor))
    if len(visited) != len(graph):
        raise ValueError("graph is disconnected")
    return total, tree


if __name__ == "__main__":
    sample = {
        "A": [("B", 1), ("C", 4)],
        "B": [("A", 1), ("C", 2), ("D", 5)],
        "C": [("A", 4), ("B", 2), ("D", 1)],
        "D": [("B", 5), ("C", 1)],
    }
    print("Prim minimum spanning tree")
    print(prim(sample, "A"))
