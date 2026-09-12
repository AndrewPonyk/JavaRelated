from dataclasses import dataclass


@dataclass(frozen=True)
class Edge:
    source: int
    target: int
    weight: int


class DisjointSet:
    def __init__(self, size: int) -> None:
        self.parent = list(range(size))
        self.rank = [0] * size

    def find(self, value: int) -> int:
        if self.parent[value] != value:
            self.parent[value] = self.find(self.parent[value])
        return self.parent[value]

    def union(self, left: int, right: int) -> bool:
        root_left = self.find(left)
        root_right = self.find(right)
        if root_left == root_right:
            return False
        if self.rank[root_left] < self.rank[root_right]:
            root_left, root_right = root_right, root_left
        self.parent[root_right] = root_left
        if self.rank[root_left] == self.rank[root_right]:
            self.rank[root_left] += 1
        return True


def kruskal_mst(vertex_count: int, edges: list[Edge]) -> dict[str, object]:
    if vertex_count < 0:
        raise ValueError("vertex_count must be non-negative")

    disjoint_set = DisjointSet(vertex_count)
    selected: list[Edge] = []

    for edge in sorted(edges, key=lambda item: item.weight):
        if edge.source >= vertex_count or edge.target >= vertex_count:
            raise ValueError("edge endpoint is outside the graph")
        if disjoint_set.union(edge.source, edge.target):
            selected.append(edge)
            if len(selected) == vertex_count - 1:
                break

    return {
        "edges": selected,
        "total_weight": sum(edge.weight for edge in selected),
    }
