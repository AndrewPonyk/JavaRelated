from __future__ import annotations


def tarjan_scc(graph: dict[str, list[str]]) -> list[list[str]]:
    index = 0
    stack: list[str] = []
    on_stack: set[str] = set()
    indices: dict[str, int] = {}
    lowlink: dict[str, int] = {}
    components: list[list[str]] = []

    def strongconnect(vertex: str) -> None:
        nonlocal index
        indices[vertex] = index
        lowlink[vertex] = index
        index += 1
        stack.append(vertex)
        on_stack.add(vertex)

        for neighbor in graph.get(vertex, []):
            if neighbor not in indices:
                strongconnect(neighbor)
                lowlink[vertex] = min(lowlink[vertex], lowlink[neighbor])
            elif neighbor in on_stack:
                lowlink[vertex] = min(lowlink[vertex], indices[neighbor])

        if lowlink[vertex] == indices[vertex]:
            component = []
            while True:
                node = stack.pop()
                on_stack.remove(node)
                component.append(node)
                if node == vertex:
                    break
            components.append(component)

    for vertex in graph:
        if vertex not in indices:
            strongconnect(vertex)
    return components


if __name__ == "__main__":
    sample = {"A": ["B"], "B": ["C"], "C": ["A", "D"], "D": []}
    print("Tarjan strongly connected components")
    print(tarjan_scc(sample))

