from __future__ import annotations

from collections import deque


def _bfs(residual: list[list[int]], source: int, sink: int, parent: list[int]) -> bool:
    visited = {source}
    queue = deque([source])
    while queue:
        current = queue.popleft()
        for vertex, capacity in enumerate(residual[current]):
            if vertex not in visited and capacity > 0:
                parent[vertex] = current
                if vertex == sink:
                    return True
                visited.add(vertex)
                queue.append(vertex)
    return False


def edmonds_karp(capacity: list[list[int]], source: int, sink: int) -> int:
    _validate_capacity(capacity, source, sink)
    residual = [row[:] for row in capacity]
    max_flow = 0

    while True:
        parent = [-1] * len(capacity)
        if not _bfs(residual, source, sink, parent):
            return max_flow
        path_flow = 10**9
        vertex = sink
        while vertex != source:
            previous = parent[vertex]
            path_flow = min(path_flow, residual[previous][vertex])
            vertex = previous
        vertex = sink
        while vertex != source:
            previous = parent[vertex]
            residual[previous][vertex] -= path_flow
            residual[vertex][previous] += path_flow
            vertex = previous
        max_flow += path_flow


def _validate_capacity(capacity: list[list[int]], source: int, sink: int) -> None:
    if not capacity:
        raise ValueError("capacity matrix is required")
    if any(len(row) != len(capacity) for row in capacity):
        raise ValueError("capacity matrix must be square")
    if source == sink:
        raise ValueError("source and sink must be different")
    if source < 0 or sink < 0 or source >= len(capacity) or sink >= len(capacity):
        raise ValueError("source and sink must be valid vertex indexes")
    if any(value < 0 for row in capacity for value in row):
        raise ValueError("capacities must be non-negative")


if __name__ == "__main__":
    capacities = [
        [0, 16, 13, 0, 0, 0],
        [0, 0, 10, 12, 0, 0],
        [0, 4, 0, 0, 14, 0],
        [0, 0, 9, 0, 0, 20],
        [0, 0, 0, 7, 0, 4],
        [0, 0, 0, 0, 0, 0],
    ]
    print("Edmonds-Karp max flow")
    print(edmonds_karp(capacities, 0, 5))
