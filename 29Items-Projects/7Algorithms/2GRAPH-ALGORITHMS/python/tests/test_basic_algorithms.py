from graph_algorithms.bellman_ford import bellman_ford
from graph_algorithms.dijkstra import dijkstra
from graph_algorithms.edmonds_karp import edmonds_karp
from graph_algorithms.kruskal import kruskal
from graph_algorithms.topological_sort import topological_sort


def test_dijkstra_shortest_paths() -> None:
    graph = {"A": [("B", 4), ("C", 2)], "B": [("D", 1)], "C": [("B", 1), ("D", 5)], "D": []}
    assert dijkstra(graph, "A")["D"] == 4


def test_bellman_ford_handles_negative_edge() -> None:
    vertices = ["A", "B", "C"]
    edges = [("A", "B", 4), ("A", "C", 5), ("B", "C", -2)]
    assert bellman_ford(vertices, edges, "A")["C"] == 2


def test_topological_sort_orders_dependencies() -> None:
    order = topological_sort({"plan": ["code"], "code": ["test"], "test": []})
    assert order.index("plan") < order.index("code") < order.index("test")


def test_kruskal_mst_weight() -> None:
    vertices = ["A", "B", "C", "D"]
    edges = [("A", "B", 1), ("A", "C", 4), ("B", "C", 2), ("C", "D", 1)]
    assert kruskal(vertices, edges)[0] == 4


def test_edmonds_karp_max_flow() -> None:
    capacities = [
        [0, 16, 13, 0, 0, 0],
        [0, 0, 10, 12, 0, 0],
        [0, 4, 0, 0, 14, 0],
        [0, 0, 9, 0, 0, 20],
        [0, 0, 0, 7, 0, 4],
        [0, 0, 0, 0, 0, 0],
    ]
    assert edmonds_karp(capacities, 0, 5) == 23
