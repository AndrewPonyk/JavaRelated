from __future__ import annotations


def articulation_points(graph: dict[str, list[str]]) -> set[str]:
    visited: set[str] = set()
    discovery: dict[str, int] = {}
    low: dict[str, int] = {}
    parent: dict[str, str | None] = {}
    points: set[str] = set()
    time = 0

    def dfs(vertex: str) -> None:
        nonlocal time
        visited.add(vertex)
        discovery[vertex] = low[vertex] = time
        time += 1
        children = 0
        for neighbor in graph.get(vertex, []):
            if neighbor not in visited:
                parent[neighbor] = vertex
                children += 1
                dfs(neighbor)
                low[vertex] = min(low[vertex], low[neighbor])
                if parent.get(vertex) is None and children > 1:
                    points.add(vertex)
                if parent.get(vertex) is not None and low[neighbor] >= discovery[vertex]:
                    points.add(vertex)
            elif neighbor != parent.get(vertex):
                low[vertex] = min(low[vertex], discovery[neighbor])

    for vertex in graph:
        if vertex not in visited:
            parent[vertex] = None
            dfs(vertex)
    return points


if __name__ == "__main__":
    sample = {"A": ["B"], "B": ["A", "C", "D"], "C": ["B"], "D": ["B"]}
    print("Articulation points")
    print(articulation_points(sample))

