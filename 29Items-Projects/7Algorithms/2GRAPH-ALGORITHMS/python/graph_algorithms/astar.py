from __future__ import annotations

import heapq
from math import inf
from typing import Callable


def astar(
    graph: dict[str, list[tuple[str, int]]],
    start: str,
    goal: str,
    heuristic: Callable[[str, str], float],
) -> tuple[float, list[str]]:
    if start not in graph:
        raise ValueError(f"start vertex {start!r} is not in the graph")
    if goal not in graph:
        raise ValueError(f"goal vertex {goal!r} is not in the graph")
    open_set = [(heuristic(start, goal), 0, start)]
    came_from: dict[str, str] = {}
    g_score = {vertex: inf for vertex in graph}
    g_score[start] = 0

    while open_set:
        _, current_cost, current = heapq.heappop(open_set)
        if current == goal:
            path = [current]
            while current in came_from:
                current = came_from[current]
                path.append(current)
            return current_cost, list(reversed(path))

        for neighbor, weight in graph[current]:
            if neighbor not in graph:
                raise ValueError(f"edge references unknown vertex {neighbor!r}")
            if weight < 0:
                raise ValueError("A* does not support negative edge weights")
            tentative = current_cost + weight
            if tentative < g_score.get(neighbor, inf):
                came_from[neighbor] = current
                g_score[neighbor] = tentative
                priority = tentative + heuristic(neighbor, goal)
                heapq.heappush(open_set, (priority, tentative, neighbor))
    return inf, []


if __name__ == "__main__":
    sample = {
        "A": [("B", 1), ("C", 4)],
        "B": [("D", 2)],
        "C": [("D", 1)],
        "D": [],
    }
    def zero_heuristic(_node: str, _goal: str) -> int:
        return 0

    print("A* path from A to D")
    print(astar(sample, "A", "D", zero_heuristic))
