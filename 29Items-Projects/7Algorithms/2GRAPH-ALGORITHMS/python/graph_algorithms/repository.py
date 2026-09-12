from __future__ import annotations

import sqlite3
import tempfile
import os
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class AlgorithmRun:
    id: int
    algorithm_name: str
    language: str
    input_summary: str
    output_summary: str
    created_at: str


class AlgorithmRunRepository:
    def __init__(self, database_path: str | Path | None = None) -> None:
        self.database_path = Path(database_path) if database_path else default_database_path()
        self.initialize()

    def initialize(self) -> None:
        self.database_path.parent.mkdir(parents=True, exist_ok=True)
        with self._connect() as connection:
            connection.execute(
                """
                CREATE TABLE IF NOT EXISTS algorithm_runs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    algorithm_name TEXT NOT NULL CHECK(length(trim(algorithm_name)) > 0),
                    language TEXT NOT NULL CHECK(language IN ('python', 'java')),
                    input_summary TEXT NOT NULL,
                    output_summary TEXT NOT NULL,
                    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """
            )
            connection.execute(
                """
                CREATE INDEX IF NOT EXISTS idx_algorithm_runs_created_at
                ON algorithm_runs (created_at)
                """
            )

    def create(self, algorithm_name: str, language: str, input_summary: str, output_summary: str) -> AlgorithmRun:
        self._validate(algorithm_name, language, input_summary, output_summary)
        with self._connect() as connection:
            cursor = connection.execute(
                """
                INSERT INTO algorithm_runs (algorithm_name, language, input_summary, output_summary)
                VALUES (?, ?, ?, ?)
                """,
                (algorithm_name.strip(), language, input_summary, output_summary),
            )
            run_id = int(cursor.lastrowid)
        run = self.get(run_id)
        if run is None:
            raise RuntimeError("Algorithm run was not persisted")
        return run

    def get(self, run_id: int) -> AlgorithmRun | None:
        with self._connect() as connection:
            row = connection.execute("SELECT * FROM algorithm_runs WHERE id = ?", (run_id,)).fetchone()
        return self._row_to_run(row) if row else None

    def list(self, limit: int = 20) -> list[AlgorithmRun]:
        if limit < 1:
            raise ValueError("limit must be positive")
        with self._connect() as connection:
            rows = connection.execute(
                "SELECT * FROM algorithm_runs ORDER BY id DESC LIMIT ?",
                (limit,),
            ).fetchall()
        return [self._row_to_run(row) for row in rows]

    def update(self, run_id: int, input_summary: str, output_summary: str) -> AlgorithmRun:
        if not input_summary.strip() or not output_summary.strip():
            raise ValueError("input_summary and output_summary are required")
        with self._connect() as connection:
            cursor = connection.execute(
                """
                UPDATE algorithm_runs
                SET input_summary = ?, output_summary = ?
                WHERE id = ?
                """,
                (input_summary, output_summary, run_id),
            )
        if cursor.rowcount == 0:
            raise KeyError(f"Algorithm run {run_id} does not exist")
        run = self.get(run_id)
        if run is None:
            raise RuntimeError("Algorithm run disappeared after update")
        return run

    def delete(self, run_id: int) -> bool:
        with self._connect() as connection:
            cursor = connection.execute("DELETE FROM algorithm_runs WHERE id = ?", (run_id,))
        return cursor.rowcount > 0

    def _connect(self) -> sqlite3.Connection:
        connection = sqlite3.connect(self.database_path)
        connection.row_factory = sqlite3.Row
        return connection

    @staticmethod
    def _validate(algorithm_name: str, language: str, input_summary: str, output_summary: str) -> None:
        if not algorithm_name.strip():
            raise ValueError("algorithm_name is required")
        if language not in {"python", "java"}:
            raise ValueError("language must be 'python' or 'java'")
        if not input_summary.strip():
            raise ValueError("input_summary is required")
        if not output_summary.strip():
            raise ValueError("output_summary is required")

    @staticmethod
    def _row_to_run(row: sqlite3.Row) -> AlgorithmRun:
        return AlgorithmRun(
            id=int(row["id"]),
            algorithm_name=str(row["algorithm_name"]),
            language=str(row["language"]),
            input_summary=str(row["input_summary"]),
            output_summary=str(row["output_summary"]),
            created_at=str(row["created_at"]),
        )


def default_database_path() -> Path:
    configured = os.environ.get("ALGORITHM_RUNS_DB")
    if configured:
        return Path(configured)
    return Path(tempfile.gettempdir()) / "graph-algorithms" / "algorithm_runs.db"
