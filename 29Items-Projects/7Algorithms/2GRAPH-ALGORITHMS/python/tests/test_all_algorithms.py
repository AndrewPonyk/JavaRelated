from math import inf
from pathlib import Path
import sqlite3

import pytest

from graph_algorithms.astar import astar
from graph_algorithms.articulation_points import articulation_points
from graph_algorithms.benchmark import benchmark_dijkstra
from graph_algorithms.bipartite_matching import bipartite_matching
from graph_algorithms.bridges import bridges
from graph_algorithms.cli import ALGORITHM_NAMES, _stable_text, main, run_algorithm
from graph_algorithms.dijkstra import dijkstra
from graph_algorithms.edmonds_karp import edmonds_karp
from graph_algorithms.floyd_warshall import floyd_warshall
from graph_algorithms.ford_fulkerson import ford_fulkerson
from graph_algorithms.graph import Graph
from graph_algorithms.kosaraju_scc import kosaraju_scc
from graph_algorithms.kruskal import kruskal
from graph_algorithms.prim import prim
from graph_algorithms.repository import AlgorithmRunRepository, default_database_path
from graph_algorithms.samples import matrix
from graph_algorithms.tarjan_scc import tarjan_scc
from graph_algorithms.topological_sort import topological_sort


def test_every_cli_algorithm_runs() -> None:
    for name in ALGORITHM_NAMES:
        assert run_algorithm(name) is not None


def test_astar_returns_expected_path() -> None:
    graph = {"A": [("B", 1), ("C", 4)], "B": [("D", 2)], "C": [("D", 1)], "D": []}
    assert astar(graph, "A", "D", lambda _node, _goal: 0) == (3, ["A", "B", "D"])


def test_floyd_warshall_all_pairs() -> None:
    result = floyd_warshall(["A", "B", "C"], [("A", "B", 3), ("B", "C", 1), ("A", "C", 10)])
    assert result["A"]["C"] == 4
    assert result["C"]["A"] == inf


def test_mst_algorithms() -> None:
    graph = {
        "A": [("B", 1), ("C", 4)],
        "B": [("A", 1), ("C", 2), ("D", 5)],
        "C": [("A", 4), ("B", 2), ("D", 1)],
        "D": [("B", 5), ("C", 1)],
    }
    vertices = ["A", "B", "C", "D"]
    edges = [("A", "B", 1), ("A", "C", 4), ("B", "C", 2), ("B", "D", 5), ("C", "D", 1)]
    assert prim(graph, "A")[0] == 4
    assert kruskal(vertices, edges)[0] == 4


def test_scc_algorithms_find_component() -> None:
    graph = {"A": ["B"], "B": ["C"], "C": ["A", "D"], "D": []}
    assert any(set(component) == {"A", "B", "C"} for component in tarjan_scc(graph))
    assert any(set(component) == {"A", "B", "C"} for component in kosaraju_scc(graph))


def test_articulation_points_and_bridges() -> None:
    graph = {"A": ["B"], "B": ["A", "C", "D"], "C": ["B"], "D": ["B"]}
    assert articulation_points(graph) == {"B"}
    assert set(bridges(graph)) == {("A", "B"), ("B", "C"), ("B", "D")}


def test_max_flow_algorithms() -> None:
    assert ford_fulkerson(matrix(), 0, 5) == 23
    assert edmonds_karp(matrix(), 0, 5) == 23


def test_bipartite_matching_size() -> None:
    result = bipartite_matching({"u1": ["v1", "v2"], "u2": ["v1"], "u3": ["v2", "v3"]})
    assert len(result) == 3
    assert set(result) == {"u1", "u2", "u3"}


def test_graph_helper_edges_are_unique_for_undirected_graph() -> None:
    graph = Graph()
    graph.add_edge("A", "B", 7)
    assert graph.vertices() == ["A", "B"]
    assert graph.edges() == [("A", "B", 7)]


def test_input_validation() -> None:
    with pytest.raises(ValueError):
        dijkstra({"A": [("B", -1)], "B": []}, "A")
    with pytest.raises(ValueError):
        topological_sort({"A": ["B"], "B": ["A"]})
    with pytest.raises(ValueError):
        ford_fulkerson([[0, 1]], 0, 1)
    with pytest.raises(ValueError):
        kruskal(["A", "B"], [])


def test_repository_crud(tmp_path) -> None:
    repository = AlgorithmRunRepository(tmp_path / "runs.db")
    created = repository.create("dijkstra", "python", "input", "output")
    assert repository.get(created.id) == created
    updated = repository.update(created.id, "new input", "new output")
    assert updated.input_summary == "new input"
    assert repository.list() == [updated]
    assert repository.delete(created.id)
    assert repository.get(created.id) is None


def test_repository_validates_inputs(tmp_path) -> None:
    repository = AlgorithmRunRepository(tmp_path / "runs.db")
    with pytest.raises(ValueError):
        repository.create("", "python", "input", "output")
    with pytest.raises(ValueError):
        repository.create("dijkstra", "ruby", "input", "output")
    with pytest.raises(KeyError):
        repository.update(999, "input", "output")


def test_repository_default_path_can_use_environment(tmp_path, monkeypatch) -> None:
    configured = tmp_path / "configured.db"
    monkeypatch.setenv("ALGORITHM_RUNS_DB", str(configured))
    assert default_database_path() == configured


def test_database_migration_sql_executes(tmp_path) -> None:
    migration = Path("migrations/001_algorithm_runs.sql").read_text(encoding="utf-8")
    with sqlite3.connect(tmp_path / "migration.db") as connection:
        connection.executescript(migration)
        indexes = connection.execute("PRAGMA index_list(algorithm_runs)").fetchall()
    assert any("idx_algorithm_runs_created_at" in row[1] for row in indexes)


def test_cli_run_and_history(tmp_path, capsys) -> None:
    db = tmp_path / "runs.db"
    assert main(["run", "--algorithm", "dijkstra", "--db", str(db)]) == 0
    assert main(["history", "--db", str(db)]) == 0
    output = capsys.readouterr().out
    assert "dijkstra" in output


def test_cli_output_is_strict_json_safe() -> None:
    text = _stable_text({"distance": inf, "nodes": {"B", "A"}})
    assert text == '{"distance": "Infinity", "nodes": ["A", "B"]}'


def test_benchmark_returns_elapsed_time() -> None:
    result = benchmark_dijkstra(10)
    assert result["name"] == "dijkstra"
    assert result["elapsed_ms"] >= 0
