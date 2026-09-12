from __future__ import annotations

from time import perf_counter
from typing import Callable

from graph_algorithms.dijkstra import dijkstra


def make_weighted_chain(size: int) -> dict[str, list[tuple[str, int]]]:
    if size < 2:
        raise ValueError("size must be at least 2")
    graph = {str(i): [] for i in range(size)}
    for i in range(size - 1):
        graph[str(i)].append((str(i + 1), 1))
    return graph


def benchmark_dijkstra(size: int = 100) -> dict[str, float]:
    return measure("dijkstra", lambda: dijkstra(make_weighted_chain(size), "0"))


def measure(name: str, action: Callable[[], object]) -> dict[str, float]:
    started = perf_counter()
    action()
    elapsed_ms = (perf_counter() - started) * 1000
    return {"name": name, "elapsed_ms": round(elapsed_ms, 3)}

