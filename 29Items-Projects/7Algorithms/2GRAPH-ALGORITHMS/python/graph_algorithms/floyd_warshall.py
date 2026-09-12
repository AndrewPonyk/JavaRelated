from __future__ import annotations

from math import inf


def floyd_warshall(vertices: list[str], edges: list[tuple[str, str, int]]) -> dict[str, dict[str, float]]:
    if len(vertices) != len(set(vertices)):
        raise ValueError("vertices must be unique")
    distance = {i: {j: inf for j in vertices} for i in vertices}
    for vertex in vertices:
        distance[vertex][vertex] = 0
    for source, target, weight in edges:
        if source not in distance or target not in distance:
            raise ValueError("edge references an unknown vertex")
        distance[source][target] = min(distance[source][target], weight)

    for middle in vertices:
        for source in vertices:
            for target in vertices:
                candidate = distance[source][middle] + distance[middle][target]
                if candidate < distance[source][target]:
                    distance[source][target] = candidate
    return distance


if __name__ == "__main__":
    nodes = ["A", "B", "C", "D"]
    weighted_edges = [("A", "B", 3), ("A", "C", 10), ("B", "C", 1), ("C", "D", 2), ("D", "B", -4)]
    print("Floyd-Warshall all-pairs shortest paths")
    print(floyd_warshall(nodes, weighted_edges))
