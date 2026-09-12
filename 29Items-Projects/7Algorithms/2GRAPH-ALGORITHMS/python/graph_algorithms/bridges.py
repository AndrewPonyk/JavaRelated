from __future__ import annotations


def bridges(graph: dict[str, list[str]]) -> list[tuple[str, str]]:
    visited: set[str] = set()
    discovery: dict[str, int] = {}
    low: dict[str, int] = {}
    parent: dict[str, str | None] = {}
    result: list[tuple[str, str]] = []
    time = 0

    def dfs(vertex: str) -> None:
        nonlocal time
        visited.add(vertex)
        discovery[vertex] = low[vertex] = time
        time += 1
        for neighbor in graph.get(vertex, []):
            if neighbor not in visited:
                parent[neighbor] = vertex
                dfs(neighbor)
                low[vertex] = min(low[vertex], low[neighbor])
                if low[neighbor] > discovery[vertex]:
                    result.append((vertex, neighbor))
            elif neighbor != parent.get(vertex):
                low[vertex] = min(low[vertex], discovery[neighbor])

    for vertex in graph:
        if vertex not in visited:
            parent[vertex] = None
            dfs(vertex)
    return result


if __name__ == "__main__":
    sample = {"A": ["B"], "B": ["A", "C", "D"], "C": ["B"], "D": ["B"]}
    print("Bridges")
    print(bridges(sample))

