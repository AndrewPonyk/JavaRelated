"""Service-level persistence over the sqlite_env fixture: the same code paths
that run against PostgreSQL in production, minus the dialect."""

from __future__ import annotations

import pandas as pd
import pytest

from app.core.config import Settings
from app.core.errors import AnalysisError, DataValidationError
from app.services import analysis_service, dataset_service, experiment_service


class TestDatasetLibrary:
    def test_save_list_load_roundtrip(self, sqlite_env: Settings, ab_df: pd.DataFrame) -> None:
        dataset_id = dataset_service.save_dataset(ab_df, "roundtrip")
        assert dataset_id

        saved = dataset_service.list_saved()
        assert [s.name for s in saved] == ["roundtrip"]
        assert saved[0].row_count == len(ab_df)

        loaded, name = dataset_service.load_saved(dataset_id)
        assert name == "roundtrip"
        pd.testing.assert_frame_equal(loaded, ab_df)

    def test_save_deduplicates_by_content_hash(
        self, sqlite_env: Settings, ab_df: pd.DataFrame
    ) -> None:
        first = dataset_service.save_dataset(ab_df, "original")
        second = dataset_service.save_dataset(ab_df.copy(), "same-data-different-name")
        assert first == second
        assert len(dataset_service.list_saved()) == 1

    def test_delete_saved(self, sqlite_env: Settings, ab_df: pd.DataFrame) -> None:
        dataset_id = dataset_service.save_dataset(ab_df, "to-delete")
        assert dataset_service.delete_saved(dataset_id) is True
        assert dataset_service.list_saved() == []
        with pytest.raises(DataValidationError):
            dataset_service.load_saved(dataset_id)

    def test_demo_mode_save_is_noop(self, ab_df: pd.DataFrame) -> None:
        # No sqlite_env fixture: settings resolve to demo mode.
        assert dataset_service.save_dataset(ab_df, "ignored") == ""
        assert dataset_service.list_saved() == []


class TestAnalysisRunPersistence:
    def test_group_comparison_is_recorded(self, sqlite_env: Settings, ab_df: pd.DataFrame) -> None:
        dataset_id = dataset_service.save_dataset(ab_df, "analyzed")
        bundle = analysis_service.run_group_comparison(
            ab_df, "session_minutes", "variant", dataset_id=dataset_id
        )
        assert bundle.result.p_value < 0.05

        runs = analysis_service.list_recent_runs()
        assert len(runs) == 1
        assert runs[0].kind == "hypothesis"
        assert runs[0].dataset_name == "analyzed"
        assert "p=" in runs[0].headline

    def test_regression_and_distribution_recorded(
        self, sqlite_env: Settings, ab_df: pd.DataFrame
    ) -> None:
        pytest.importorskip("statsmodels")
        dataset_id = dataset_service.save_dataset(ab_df, "multi")
        analysis_service.run_regression(
            ab_df, "session_minutes", ["converted"], dataset_id=dataset_id
        )
        analysis_service.run_distribution_fit(
            ab_df, "session_minutes", candidate_names=["normal"], dataset_id=dataset_id
        )
        kinds = {run.kind for run in analysis_service.list_recent_runs()}
        assert kinds == {"regression", "distribution"}

    def test_no_dataset_id_skips_persistence(
        self, sqlite_env: Settings, ab_df: pd.DataFrame
    ) -> None:
        analysis_service.run_group_comparison(ab_df, "session_minutes", "variant")
        assert analysis_service.list_recent_runs() == []

    def test_unknown_model_kind_rejected(self, ab_df: pd.DataFrame) -> None:
        with pytest.raises(AnalysisError):
            analysis_service.run_regression(
                ab_df, "session_minutes", ["converted"], model_kind="poisson"
            )

    def test_headline_formats(self) -> None:
        assert "p=0.01" in analysis_service._headline(
            "hypothesis", {"test": "student_t", "p_value": 0.01}
        )
        assert "R²=0.500" in analysis_service._headline("regression", {"r_squared": 0.5})
        assert "lognormal" in analysis_service._headline(
            "distribution", {"fits": [{"name": "lognormal", "aic": 123.0}]}
        )


class TestExperimentService:
    def test_crud_and_lifecycle(self, sqlite_env: Settings) -> None:
        experiment_id = experiment_service.create_experiment(
            "checkout-v2",
            hypothesis="B converts better",
            primary_metric="converted",
            planned_n_per_variant=1000,
            expected_ratio=0.5,
        )
        listed = experiment_service.list_experiments()
        assert [e.name for e in listed] == ["checkout-v2"]
        assert listed[0].status == "draft"

        running = experiment_service.advance_status(experiment_id, "running")
        assert running.status == "running"

        with pytest.raises(DataValidationError):
            experiment_service.advance_status(experiment_id, "draft")

        assert experiment_service.delete_experiment(experiment_id) is True
        assert experiment_service.list_experiments() == []

    def test_duplicate_name_rejected(self, sqlite_env: Settings) -> None:
        experiment_service.create_experiment("dup")
        with pytest.raises(DataValidationError):
            experiment_service.create_experiment("dup")

    def test_demo_mode_returns_empty(self) -> None:
        assert experiment_service.list_experiments() == []
