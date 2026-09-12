"""Repository round-trips against a real PostgreSQL.

Locally:  docker compose -f docker/docker-compose.yml up -d db
          set TEST_DATABASE_URL=postgresql+psycopg2://stats:stats@localhost:5432/stats_dashboard
CI:       provided by the postgres service container (see .github/workflows/ci.yml).

NOTE: teardown drops all app tables — point TEST_DATABASE_URL at a throwaway DB only.
"""

from __future__ import annotations

import os

import pytest
import sqlalchemy as sa
from sqlalchemy.orm import Session

from app.data.models import AnalysisRun, Base, Dataset
from app.data.repositories import AnalysisRunRepository, DatasetRepository

pytestmark = pytest.mark.integration

DATABASE_URL = os.getenv("TEST_DATABASE_URL")


@pytest.fixture(scope="module")
def engine():
    if not DATABASE_URL:
        pytest.skip("TEST_DATABASE_URL not set — see module docstring")
    engine = sa.create_engine(DATABASE_URL)
    Base.metadata.create_all(engine)  # no-op when migrations already applied
    yield engine
    Base.metadata.drop_all(engine)
    engine.dispose()


def test_dataset_roundtrip_with_jsonb(engine) -> None:
    with Session(engine) as session:
        created = DatasetRepository(session).add(
            Dataset(
                name="it-dataset",
                source_type="upload",
                row_count=3,
                column_count=2,
                schema_json={"y": "continuous", "g": "binary"},
            )
        )
        session.commit()
        dataset_id = created.id

    with Session(engine) as session:
        loaded = DatasetRepository(session).get(dataset_id)
        assert loaded is not None
        assert loaded.schema_json == {"y": "continuous", "g": "binary"}


def test_analysis_runs_link_to_dataset(engine) -> None:
    with Session(engine) as session:
        dataset = DatasetRepository(session).add(
            Dataset(name="it-runs", row_count=1, column_count=1)
        )
        run_repo = AnalysisRunRepository(session)
        run_repo.add(
            AnalysisRun(
                dataset_id=dataset.id,
                kind="hypothesis",
                params_json={"outcome": "y", "group": "g", "alpha": 0.05},
                results_json={"test": "student_t", "p_value": 0.012},
                duration_ms=42,
            )
        )
        session.commit()
        dataset_id = dataset.id

    with Session(engine) as session:
        runs = AnalysisRunRepository(session).list_for_dataset(dataset_id)
        assert len(runs) == 1
        assert runs[0].results_json["test"] == "student_t"
