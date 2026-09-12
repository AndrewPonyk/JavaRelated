from __future__ import annotations

from typing import Any


CAPACITY_MATRIX = [
    [0, 16, 13, 0, 0, 0],
    [0, 0, 10, 12, 0, 0],
    [0, 4, 0, 0, 14, 0],
    [0, 0, 9, 0, 0, 20],
    [0, 0, 0, 7, 0, 4],
    [0, 0, 0, 0, 0, 0],
]


def weighted_directed_graph() -> dict[str, list[tuple[str, int]]]:
    return {
        "A": [("B", 4), ("C", 2)],
        "B": [("C", 1), ("D", 5)],
        "C": [("D", 8), ("E", 10)],
        "D": [("E", 2)],
        "E": [],
    }


def weighted_edges() -> tuple[list[str], list[tuple[str, str, int]]]:
    return (
        ["A", "B", "C", "D", "E"],
        [("A", "B", 4), ("A", "C", 2), ("B", "C", 1), ("B", "D", 5), ("D", "E", 2)],
    )


def weighted_undirected_graph() -> dict[str, list[tuple[str, int]]]:
    return {
        "A": [("B", 1), ("C", 4)],
        "B": [("A", 1), ("C", 2), ("D", 5)],
        "C": [("A", 4), ("B", 2), ("D", 1)],
        "D": [("B", 5), ("C", 1)],
    }


def weighted_undirected_edges() -> tuple[list[str], list[tuple[str, str, int]]]:
    return (
        ["A", "B", "C", "D"],
        [("A", "B", 1), ("A", "C", 4), ("B", "C", 2), ("B", "D", 5), ("C", "D", 1)],
    )


def dag() -> dict[str, list[str]]:
    return {"plan": ["code"], "code": ["test"], "test": ["ship"], "ship": []}


def strongly_connected_graph() -> dict[str, list[str]]:
    return {"A": ["B"], "B": ["C"], "C": ["A", "D"], "D": []}


def undirected_unweighted_graph() -> dict[str, list[str]]:
    return {"A": ["B"], "B": ["A", "C", "D"], "C": ["B"], "D": ["B"]}


def bipartite_graph() -> dict[str, list[str]]:
    return {"u1": ["v1", "v2"], "u2": ["v1"], "u3": ["v2", "v3"]}


def matrix() -> list[list[int]]:
    return [row[:] for row in CAPACITY_MATRIX]


def json_example() -> dict[str, Any]:
    vertices, edges = weighted_edges()
    return {"vertices": vertices, "edges": edges, "start": "A", "goal": "E"}

