"""EMR Serverless entrypoint: run any lakehouse job module by name.

EMR's sparkSubmit driver requires a single entryPoint *file*; this shim turns
that into `python -m <module> <args...>` semantics so Airflow and ad-hoc
submissions use the exact same job entrypoints as local runs.

deploy.yml uploads this file to s3://<artifacts>/entrypoints/run_module.py and
jobs are invoked as: run_module.py lakehouse.jobs.bronze_to_silver --run-date ...
"""

from __future__ import annotations

import runpy
import sys


def main() -> None:
    if len(sys.argv) < 2:
        raise SystemExit("usage: run_module.py <python.module> [args...]")
    module = sys.argv[1]
    sys.argv = [module, *sys.argv[2:]]
    runpy.run_module(module, run_name="__main__", alter_sys=True)


if __name__ == "__main__":
    main()
