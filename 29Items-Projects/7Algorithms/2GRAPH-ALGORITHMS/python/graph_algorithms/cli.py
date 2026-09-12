from __future__ import annotations

import argparse
import json
import math
from pathlib import Path
from typing import Any

from graph_algorithms.astar import astar
from graph_algorithms.bellman_ford import bellman_ford
from graph_algorithms.benchmark import benchmark_dijkstra
from graph_algorithms.bipartite_matching import bipartite_matching
from graph_algorithms.bridges import bridges
from graph_algorithms.dijkstra import dijkstra
from graph_algorithms.edmonds_karp import edmonds_karp
from graph_algorithms.floyd_warshall import floyd_warshall
from graph_algorithms.ford_fulkerson import ford_fulkerson
from graph_algorithms.kosaraju_scc import kosaraju_scc
from graph_algorithms.kruskal import kruskal
from graph_algorithms.prim import prim
from graph_algorithms.repository import AlgorithmRunRepository
from graph_algorithms.samples import (
    bipartite_graph,
    dag,
    matrix,
    strongly_connected_graph,
    undirected_unweighted_graph,
    weighted_directed_graph,
    weighted_edges,
    weighted_undirected_edges,
    weighted_undirected_graph,
)
from graph_algorithms.tarjan_scc import tarjan_scc
from graph_algorithms.topological_sort import topological_sort
from graph_algorithms.articulation_points import articulation_points


ALGORITHM_NAMES = [
    "astar",
    "articulation-points",
    "bellman-ford",
    "bipartite-matching",
    "bridges",
    "dijkstra",
    "edmonds-karp",
    "floyd-warshall",
    "ford-fulkerson",
    "kosaraju-scc",
    "kruskal",
    "prim",
    "tarjan-scc",
    "topological-sort",
]


def run_algorithm(name: str, payload: dict[str, Any] | None = None) -> object:
    if name == "dijkstra":
        graph = _weighted_graph_from_payload(payload) if payload else weighted_directed_graph()
        return dijkstra(graph, str(payload.get("start", "A")) if payload else "A")
    if name == "astar":
        graph = _weighted_graph_from_payload(payload) if payload else weighted_directed_graph()
        start = str(payload.get("start", "A")) if payload else "A"
        goal = str(payload.get("goal", "E")) if payload else "E"
        return astar(graph, start, goal, lambda _node, _goal: 0)
    if name == "floyd-warshall":
        vertices, edges = _vertices_edges_from_payload(payload) if payload else weighted_edges()
        return floyd_warshall(vertices, edges)
    if name == "bellman-ford":
        vertices, edges = _vertices_edges_from_payload(payload) if payload else weighted_edges()
        start = str(payload.get("start", "A")) if payload else "A"
        return bellman_ford(vertices, edges, start)
    if name == "prim":
        return prim(weighted_undirected_graph(), "A")
    if name == "kruskal":
        vertices, edges = weighted_undirected_edges()
        return kruskal(vertices, edges)
    if name == "topological-sort":
        return topological_sort(dag())
    if name == "tarjan-scc":
        return tarjan_scc(strongly_connected_graph())
    if name == "kosaraju-scc":
        return kosaraju_scc(strongly_connected_graph())
    if name == "articulation-points":
        return sorted(articulation_points(undirected_unweighted_graph()))
    if name == "bridges":
        return bridges(undirected_unweighted_graph())
    if name == "ford-fulkerson":
        return ford_fulkerson(matrix(), 0, 5)
    if name == "edmonds-karp":
        return edmonds_karp(matrix(), 0, 5)
    if name == "bipartite-matching":
        return bipartite_matching(bipartite_graph())
    raise ValueError(f"Unknown algorithm: {name}")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Run graph algorithm demos")
    parser.add_argument("command", choices=["list", "run", "run-all", "benchmark", "history"])
    parser.add_argument("--algorithm", choices=ALGORITHM_NAMES)
    parser.add_argument("--input", type=Path, help="JSON input file for dijkstra, astar, bellman-ford, or floyd-warshall")
    parser.add_argument("--db", type=Path, default=None)
    parser.add_argument("--limit", type=int, default=20)
    args = parser.parse_args(argv)

    if args.command == "list":
        print("\n".join(ALGORITHM_NAMES))
        return 0

    if args.command == "benchmark":
        print(json.dumps(benchmark_dijkstra(), indent=2))
        return 0

    repository = AlgorithmRunRepository(args.db)
    if args.command == "history":
        for run in repository.list(args.limit):
            print(f"{run.id}: {run.algorithm_name} [{run.language}] {run.output_summary}")
        return 0

    payload = _load_json(args.input) if args.input else None
    names = ALGORITHM_NAMES if args.command == "run-all" else [args.algorithm]
    if names == [None]:
        parser.error("--algorithm is required for run")

    for name in names:
        assert name is not None
        result = run_algorithm(name, payload if args.command == "run" else None)
        text = _stable_text(result)
        print(f"{name}: {text}")
        repository.create(name, "python", _stable_text(payload or "sample"), text)
    return 0


def _load_json(path: Path) -> dict[str, Any]:
    with path.open("r", encoding="utf-8") as file:
        data = json.load(file)
    if not isinstance(data, dict):
        raise ValueError("JSON input must be an object")
    return data


def _weighted_graph_from_payload(payload: dict[str, Any] | None) -> dict[str, list[tuple[str, int]]]:
    vertices, edges = _vertices_edges_from_payload(payload)
    graph = {vertex: [] for vertex in vertices}
    for source, target, weight in edges:
        graph[source].append((target, weight))
        graph.setdefault(target, [])
    return graph


def _vertices_edges_from_payload(payload: dict[str, Any] | None) -> tuple[list[str], list[tuple[str, str, int]]]:
    if payload is None:
        raise ValueError("payload is required")
    vertices = payload.get("vertices")
    edges = payload.get("edges")
    if not isinstance(vertices, list) or not all(isinstance(vertex, str) for vertex in vertices):
        raise ValueError("vertices must be a list of strings")
    if not isinstance(edges, list):
        raise ValueError("edges must be a list")
    parsed_edges = []
    for edge in edges:
        if not isinstance(edge, list | tuple) or len(edge) != 3:
            raise ValueError("each edge must be [source, target, weight]")
        source, target, weight = edge
        if not isinstance(source, str) or not isinstance(target, str) or not isinstance(weight, int):
            raise ValueError("edge source/target must be strings and weight must be an integer")
        if source not in vertices or target not in vertices:
            raise ValueError("edge references an unknown vertex")
        parsed_edges.append((source, target, weight))
    return vertices, parsed_edges


def _stable_text(value: object) -> str:
    return json.dumps(_json_safe(value), sort_keys=True, default=str, allow_nan=False)


def _json_safe(value: object) -> object:
    if isinstance(value, float) and math.isinf(value):
        return "Infinity" if value > 0 else "-Infinity"
    if isinstance(value, dict):
        return {str(key): _json_safe(item) for key, item in value.items()}
    if isinstance(value, set):
        return [_json_safe(item) for item in sorted(value)]
    if isinstance(value, tuple | list):
        return [_json_safe(item) for item in value]
    return value


if __name__ == "__main__":
    raise SystemExit(main())
