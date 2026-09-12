from __future__ import annotations

from math import inf


def bellman_ford(vertices: list[str], edges: list[tuple[str, str, int]], start: str) -> dict[str, float]:
    if start not in vertices:
        raise ValueError(f"start vertex {start!r} is not in the graph")
    if len(vertices) != len(set(vertices)):
        raise ValueError("vertices must be unique")
    distance = {vertex: inf for vertex in vertices}
    distance[start] = 0

    for _ in range(len(vertices) - 1):
        changed = False
        for source, target, weight in edges:
            if source not in distance or target not in distance:
                raise ValueError("edge references an unknown vertex")
            if distance[source] != inf and distance[source] + weight < distance[target]:
                distance[target] = distance[source] + weight
                changed = True
        if not changed:
            break

    for source, target, weight in edges:
        if source not in distance or target not in distance:
            raise ValueError("edge references an unknown vertex")
        if distance[source] != inf and distance[source] + weight < distance[target]:
            raise ValueError("Graph contains a negative weight cycle")
    return distance


if __name__ == "__main__":
    nodes = ["A", "B", "C", "D"]
    weighted_edges = [("A", "B", 4), ("A", "C", 5), ("B", "C", -2), ("C", "D", 3)]
    print("Bellman-Ford shortest paths from A")
    print(bellman_ford(nodes, weighted_edges, "A"))
