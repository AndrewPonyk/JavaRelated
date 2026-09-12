from __future__ import annotations


def kosaraju_scc(graph: dict[str, list[str]]) -> list[list[str]]:
    visited: set[str] = set()
    order: list[str] = []

    def dfs(vertex: str) -> None:
        visited.add(vertex)
        for neighbor in graph.get(vertex, []):
            if neighbor not in visited:
                dfs(neighbor)
        order.append(vertex)

    for vertex in graph:
        if vertex not in visited:
            dfs(vertex)

    reversed_graph = {vertex: [] for vertex in graph}
    for source, neighbors in graph.items():
        for target in neighbors:
            reversed_graph.setdefault(target, []).append(source)

    visited.clear()
    components: list[list[str]] = []

    def reverse_dfs(vertex: str, component: list[str]) -> None:
        visited.add(vertex)
        component.append(vertex)
        for neighbor in reversed_graph.get(vertex, []):
            if neighbor not in visited:
                reverse_dfs(neighbor, component)

    for vertex in reversed(order):
        if vertex not in visited:
            component: list[str] = []
            reverse_dfs(vertex, component)
            components.append(component)
    return components


if __name__ == "__main__":
    sample = {"A": ["B"], "B": ["C"], "C": ["A", "D"], "D": []}
    print("Kosaraju strongly connected components")
    print(kosaraju_scc(sample))

