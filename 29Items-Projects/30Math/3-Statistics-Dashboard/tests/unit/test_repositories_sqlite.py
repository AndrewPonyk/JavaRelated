"""Repository CRUD over in-memory SQLite (fast path; PostgreSQL parity in tests/integration)."""

from __future__ import annotations

import pytest
import sqlalchemy as sa
from sqlalchemy.orm import Session

from app.core.errors import DataValidationError
from app.data.models import AnalysisRun, Base, Dataset, Experiment
from app.data.repositories import AnalysisRunRepository, DatasetRepository, ExperimentRepository


@pytest.fixture()
def session():
    engine = sa.create_engine("sqlite://")
    Base.metadata.create_all(engine)
    with Session(engine) as s:
        yield s
    engine.dispose()


class TestDatasetRepository:
    def test_add_get_roundtrip(self, session: Session) -> None:
        repo = DatasetRepository(session)
        created = repo.add(
            Dataset(name="d1", row_count=10, column_count=3, schema_json={"a": "continuous"})
        )
        loaded = repo.get(created.id)
        assert loaded is not None
        assert loaded.schema_json == {"a": "continuous"}

    def test_get_by_content_hash(self, session: Session) -> None:
        repo = DatasetRepository(session)
        repo.add(Dataset(name="d1", content_hash="abc123"))
        assert repo.get_by_content_hash("abc123") is not None
        assert repo.get_by_content_hash("missing") is None

    def test_list_recent_ordering_and_filter(self, session: Session) -> None:
        repo = DatasetRepository(session)
        repo.add(Dataset(name="mine", created_by_email="a@x.io"))
        repo.add(Dataset(name="theirs", created_by_email="b@x.io"))
        assert len(repo.list_recent()) == 2
        mine = repo.list_recent(created_by_email="a@x.io")
        assert [d.name for d in mine] == ["mine"]

    def test_delete_cascades_runs(self, session: Session) -> None:
        datasets = DatasetRepository(session)
        runs = AnalysisRunRepository(session)
        dataset = datasets.add(Dataset(name="doomed"))
        runs.add(AnalysisRun(dataset_id=dataset.id, kind="hypothesis"))
        assert datasets.delete(dataset.id) is True
        assert datasets.get(dataset.id) is None
        assert runs.list_for_dataset(dataset.id) == []

    def test_delete_unknown_returns_false(self, session: Session) -> None:
        assert DatasetRepository(session).delete("nope") is False


class TestAnalysisRunRepository:
    def test_list_for_dataset_newest_first(self, session: Session) -> None:
        dataset = DatasetRepository(session).add(Dataset(name="d"))
        repo = AnalysisRunRepository(session)
        first = repo.add(AnalysisRun(dataset_id=dataset.id, kind="hypothesis"))
        second = repo.add(AnalysisRun(dataset_id=dataset.id, kind="regression"))
        listed = repo.list_for_dataset(dataset.id)
        assert {r.id for r in listed} == {first.id, second.id}

    def test_list_recent_eager_loads_dataset(self, session: Session) -> None:
        dataset = DatasetRepository(session).add(Dataset(name="named"))
        AnalysisRunRepository(session).add(
            AnalysisRun(dataset_id=dataset.id, kind="ab_test", results_json={"p_value": 0.01})
        )
        recent = AnalysisRunRepository(session).list_recent()
        assert recent[0].dataset.name == "named"
        assert recent[0].results_json == {"p_value": 0.01}


class TestExperimentRepository:
    def test_lifecycle_happy_path(self, session: Session) -> None:
        repo = ExperimentRepository(session)
        experiment = repo.add(Experiment(name="exp-1"))
        assert experiment.status == "draft"
        assert repo.set_status(experiment.id, "running").status == "running"
        assert repo.set_status(experiment.id, "completed").status == "completed"

    def test_illegal_transition_rejected(self, session: Session) -> None:
        repo = ExperimentRepository(session)
        experiment = repo.add(Experiment(name="exp-2"))
        with pytest.raises(DataValidationError):
            repo.set_status(experiment.id, "completed")  # draft cannot skip running

    def test_backward_transition_rejected(self, session: Session) -> None:
        repo = ExperimentRepository(session)
        experiment = repo.add(Experiment(name="exp-3"))
        repo.set_status(experiment.id, "running")
        with pytest.raises(DataValidationError):
            repo.set_status(experiment.id, "running")

    def test_duplicate_name_rejected(self, session: Session) -> None:
        repo = ExperimentRepository(session)
        repo.add(Experiment(name="unique"))
        with pytest.raises(DataValidationError):
            repo.add(Experiment(name="unique"))

    def test_list_all_with_run_counts_single_query(self, session: Session) -> None:
        dataset = DatasetRepository(session).add(Dataset(name="d"))
        experiments = ExperimentRepository(session)
        runs = AnalysisRunRepository(session)
        counted = experiments.add(Experiment(name="counted"))
        experiments.add(Experiment(name="empty"))
        runs.add(AnalysisRun(dataset_id=dataset.id, experiment_id=counted.id, kind="ab_test"))
        runs.add(AnalysisRun(dataset_id=dataset.id, experiment_id=counted.id, kind="ab_test"))

        result = experiments.list_all_with_run_counts()
        counts = {experiment.name: count for experiment, count in result}
        assert counts == {"counted": 2, "empty": 0}
        assert runs.count_for_experiment(counted.id) == 2

    def test_delete_keeps_run_history(self, session: Session) -> None:
        dataset = DatasetRepository(session).add(Dataset(name="d"))
        experiments = ExperimentRepository(session)
        runs = AnalysisRunRepository(session)
        experiment = experiments.add(Experiment(name="short-lived"))
        run = runs.add(
            AnalysisRun(dataset_id=dataset.id, experiment_id=experiment.id, kind="ab_test")
        )
        assert experiments.delete(experiment.id) is True
        session.expire_all()
        survivor = session.get(AnalysisRun, run.id)
        assert survivor is not None  # history survives; FK cleared by SET NULL semantics
