"""Graph Coloring — assign colors so no adjacent vertices share the same color."""

COLOR_NAMES = ["Red", "Green", "Blue", "Yellow", "Purple", "Orange", "Cyan", "White"]


class GraphColoring:
    def __init__(self, adj: list[list[int]], num_colors: int):
        self.adj = adj
        self.num_colors = num_colors
        self.v = len(adj)
        self.colors = [-1] * self.v

    def solve_and_print(self) -> None:
        print(f"Graph with {self.v} vertices, {self.num_colors} colors")
        print("Adjacency matrix:")
        for row in self.adj:
            print(f"  {row}")
        print()

        success = self._backtrack(0)

        if success:
            print("Valid coloring found:")
            for i, c in enumerate(self.colors):
                name = COLOR_NAMES[c] if c < len(COLOR_NAMES) else f"C{c}"
                print(f"  Vertex {i} → {name}")
        else:
            print(f"No valid coloring exists with {self.num_colors} colors.")

    def _backtrack(self, vertex: int) -> bool:
        if vertex == self.v:
            return True
        for c in range(self.num_colors):
            if self._is_safe(vertex, c):
                self.colors[vertex] = c
                if self._backtrack(vertex + 1):
                    return True
                self.colors[vertex] = -1
        return False

    def _is_safe(self, vertex: int, color: int) -> bool:
        for i in range(self.v):
            if self.adj[vertex][i] == 1 and self.colors[i] == color:
                return False
        return True
