"""Hamiltonian Path — visit every vertex exactly once."""


class HamiltonianPath:
    def __init__(self, adj: list[list[int]]):
        self.adj = adj
        self.v = len(adj)
        self.path = [-1] * self.v

    def solve_and_print(self) -> None:
        print("Graph adjacency matrix:")
        for row in self.adj:
            print(f"  {row}")
        print()

        self.path[0] = 0
        found = self._backtrack(1)

        if found:
            route = " → ".join(str(v) for v in self.path)
            print(f"Hamiltonian Path: {route}")
        else:
            print("No Hamiltonian Path exists.")

    def _backtrack(self, pos: int) -> bool:
        if pos == self.v:
            return True
        for nxt in range(self.v):
            if self._is_safe(nxt, pos):
                self.path[pos] = nxt
                if self._backtrack(pos + 1):
                    return True
                self.path[pos] = -1
        return False

    def _is_safe(self, vertex: int, pos: int) -> bool:
        if self.adj[self.path[pos - 1]][vertex] == 0:
            return False
        return vertex not in self.path[:pos]
