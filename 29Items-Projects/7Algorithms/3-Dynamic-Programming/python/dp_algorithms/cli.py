from __future__ import annotations

import argparse

from dp_algorithms.demo import run


def main() -> None:
    parser = argparse.ArgumentParser(description="Run dynamic programming algorithm demos.")
    parser.add_argument("--all", action="store_true", help="Run all demos. This is the default.")
    parser.parse_args()
    run()


if __name__ == "__main__":
    main()
