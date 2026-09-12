from __future__ import annotations

from collections import deque


def topological_sort(graph: dict[str, list[str]]) -> list[str]:
    indegree = {vertex: 0 for vertex in graph}
    for neighbors in graph.values():
        for neighbor in neighbors:
            indegree[neighbor] = indegree.get(neighbor, 0) + 1

    queue = deque([vertex for vertex, degree in indegree.items() if degree == 0])
    order: list[str] = []
    while queue:
        vertex = queue.popleft()
        order.append(vertex)
        for neighbor in graph.get(vertex, []):
            indegree[neighbor] -= 1
            if indegree[neighbor] == 0:
                queue.append(neighbor)

    if len(order) != len(indegree):
        raise ValueError("Graph contains a cycle")
    return order


if __name__ == "__main__":
    sample = {"plan": ["code"], "code": ["test"], "test": ["ship"], "ship": []}
    print("Topological sort")
    print(topological_sort(sample))

