from __future__ import annotations

import heapq
from math import inf


def dijkstra(graph: dict[str, list[tuple[str, int]]], start: str) -> dict[str, float]:
    if start not in graph:
        raise ValueError(f"start vertex {start!r} is not in the graph")
    distances = {vertex: inf for vertex in graph}
    distances[start] = 0
    heap = [(0, start)]

    while heap:
        current_distance, vertex = heapq.heappop(heap)
        if current_distance > distances[vertex]:
            continue
        for neighbor, weight in graph[vertex]:
            if neighbor not in graph:
                raise ValueError(f"edge references unknown vertex {neighbor!r}")
            if weight < 0:
                raise ValueError("Dijkstra does not support negative edge weights")
            distance = current_distance + weight
            if distance < distances.get(neighbor, inf):
                distances[neighbor] = distance
                heapq.heappush(heap, (distance, neighbor))
    return distances


if __name__ == "__main__":
    sample = {
        "A": [("B", 4), ("C", 2)],
        "B": [("C", 1), ("D", 5)],
        "C": [("D", 8), ("E", 10)],
        "D": [("E", 2)],
        "E": [],
    }
    print("Dijkstra shortest paths from A")
    print(dijkstra(sample, "A"))
