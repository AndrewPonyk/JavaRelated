from __future__ import annotations

from graph_algorithms.cli import ALGORITHM_NAMES, run_algorithm


def main() -> None:
    print("Graph Algorithms Python Demo")
    for name in ALGORITHM_NAMES:
        print(f"{name}: {run_algorithm(name)}")


if __name__ == "__main__":
    main()
