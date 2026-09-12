from __future__ import annotations


class DisjointSet:
    def __init__(self, vertices: list[str]) -> None:
        self.parent = {vertex: vertex for vertex in vertices}
        self.rank = {vertex: 0 for vertex in vertices}

    def find(self, vertex: str) -> str:
        if self.parent[vertex] != vertex:
            self.parent[vertex] = self.find(self.parent[vertex])
        return self.parent[vertex]

    def union(self, left: str, right: str) -> bool:
        root_left = self.find(left)
        root_right = self.find(right)
        if root_left == root_right:
            return False
        if self.rank[root_left] < self.rank[root_right]:
            self.parent[root_left] = root_right
        elif self.rank[root_left] > self.rank[root_right]:
            self.parent[root_right] = root_left
        else:
            self.parent[root_right] = root_left
            self.rank[root_left] += 1
        return True


def kruskal(vertices: list[str], edges: list[tuple[str, str, int]]) -> tuple[int, list[tuple[str, str, int]]]:
    if len(vertices) != len(set(vertices)):
        raise ValueError("vertices must be unique")
    vertex_set = set(vertices)
    for source, target, _weight in edges:
        if source not in vertex_set or target not in vertex_set:
            raise ValueError("edge references an unknown vertex")
    ds = DisjointSet(vertices)
    total = 0
    tree: list[tuple[str, str, int]] = []
    for source, target, weight in sorted(edges, key=lambda edge: edge[2]):
        if ds.union(source, target):
            total += weight
            tree.append((source, target, weight))
    if vertices and len(tree) != len(vertices) - 1:
        raise ValueError("graph is disconnected")
    return total, tree


if __name__ == "__main__":
    nodes = ["A", "B", "C", "D"]
    weighted_edges = [("A", "B", 1), ("A", "C", 4), ("B", "C", 2), ("B", "D", 5), ("C", "D", 1)]
    print("Kruskal minimum spanning tree")
    print(kruskal(nodes, weighted_edges))
