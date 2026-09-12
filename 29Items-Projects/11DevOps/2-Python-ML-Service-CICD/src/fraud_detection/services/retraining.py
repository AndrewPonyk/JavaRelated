"""Asynchronous model retraining.

Two execution backends, chosen at submission time:

* **Azure ML** (production): when the ``azure-ai-ml`` SDK is installed and
  configured, the training job spec is submitted to the workspace.
* **Local** (development/tests, and the fallback when Azure is absent):
  the real training pipeline runs on a daemon thread, registers the new
  version in the local model store as ``challenger``, records it in the
  ``model_versions`` table (best-effort) and invalidates the model cache.

Jobs are tracked in an in-memory, thread-safe registry keyed by job id
(per-process; a restarted pod forgets finished jobs, which is acceptable
because the durable outcomes - artifacts, registry aliases, DB rows -
all live outside the process).
"""

from __future__ import annotations

import random
import threading
import time
import uuid
from dataclasses import dataclass, field
from datetime import UTC, datetime
from typing import Any

from fraud_detection.core.config import get_settings
from fraud_detection.core.logging import get_logger
from fraud_detection.db.models import ModelVersion
from fraud_detection.db.repositories import ModelVersionRepository
from fraud_detection.monitoring.metrics import (
    FRAUD_DB_ERRORS_TOTAL,
    FRAUD_RETRAINING_RUNS_TOTAL,
)
from fraud_detection.services.model_loader import ModelLoader

logger = get_logger(__name__)

_TRAIN_JOB_SPEC = "mlops/azureml/train-job.yml"


@dataclass
class RetrainJob:
    """State of one retraining job."""

    job_id: str
    status: str
    reason: str
    submitted_at: datetime
    finished_at: datetime | None = None
    detail: str | None = None
    result: dict[str, Any] | None = field(default=None)


class RetrainingService:
    """Submits and tracks retraining jobs."""

    def __init__(self, model_loader: ModelLoader | None = None) -> None:
        self._model_loader = model_loader
        self._jobs: dict[str, RetrainJob] = {}
        self._lock = threading.Lock()
        self._last_auto_trigger: float | None = None

    def submit(self, reason: str, debounce: bool = False) -> RetrainJob | None:
        """Kick off model retraining.

        Args:
            reason: Trigger reason (e.g. ``"manual"`` or ``"drift:log_amount"``).
            debounce: When True (automatic drift triggers), the submission
                is dropped if the last automatic trigger is younger than
                ``retrain_min_interval_minutes``. Manual triggers bypass it.

        Returns:
            The created job, or None when debounced.
        """
        settings = get_settings()
        if debounce:
            with self._lock:
                interval_seconds = settings.retrain_min_interval_minutes * 60
                now = time.monotonic()
                if (
                    self._last_auto_trigger is not None
                    and now - self._last_auto_trigger < interval_seconds
                ):
                    logger.info("retraining_debounced", reason=reason)
                    return None
                self._last_auto_trigger = now

        job = RetrainJob(
            job_id=uuid.uuid4().hex,
            status="queued",
            reason=reason,
            submitted_at=datetime.now(UTC),
        )
        with self._lock:
            self._jobs[job.job_id] = job

        azure_job_name = self._try_azureml(reason)
        if azure_job_name is not None:
            job.status = "submitted"
            job.detail = f"azureml job {azure_job_name}"
            logger.info("retraining_submitted_azureml", job=azure_job_name, reason=reason)
            return job

        worker = threading.Thread(target=self._run_local, args=(job,), daemon=True)
        worker.start()
        return job

    def get(self, job_id: str) -> RetrainJob | None:
        """Return a tracked job by id."""
        with self._lock:
            return self._jobs.get(job_id)

    def _try_azureml(self, reason: str) -> str | None:
        """Submit the Azure ML job when the SDK is installed and configured.

        Policy: any failure (SDK missing, no credentials/config) degrades to
        the local training backend; only a successful submission returns a
        job name.
        """
        try:
            from azure.ai.ml import MLClient, load_job  # noqa: PLC0415 - lazy import
            from azure.identity import DefaultAzureCredential  # noqa: PLC0415
        except ImportError:
            return None
        try:
            client = MLClient.from_config(credential=DefaultAzureCredential())
            azure_job = load_job(_TRAIN_JOB_SPEC)
            azure_job.tags = {**(azure_job.tags or {}), "trigger_reason": reason}
            submitted = client.jobs.create_or_update(azure_job)
            return str(submitted.name)
        except Exception:  # noqa: BLE001 - policy: degrade to the local backend
            logger.warning("azureml_submission_failed_falling_back_local", reason=reason)
            return None

    def _run_local(self, job: RetrainJob) -> None:
        """Execute the local training backend for one job."""
        from fraud_detection.ml.train import QualityGateError, train_pipeline

        settings = get_settings()
        job.status = "running"
        seed = random.SystemRandom().randrange(2**31)
        try:
            summary = train_pipeline(
                model_dir=settings.model_dir,
                n_samples=settings.training_samples,
                seed=seed,
                data_path=settings.training_data_path,
                register=True,
            )
        except QualityGateError as exc:
            job.status = "failed"
            job.detail = f"quality gates failed: {'; '.join(exc.reasons)}"
            FRAUD_RETRAINING_RUNS_TOTAL.labels(status="failed").inc()
            logger.warning("retraining_failed_quality_gates", job_id=job.job_id, seed=seed)
        except Exception as exc:  # noqa: BLE001 - job status must always resolve
            job.status = "failed"
            job.detail = str(exc)
            FRAUD_RETRAINING_RUNS_TOTAL.labels(status="failed").inc()
            logger.warning("retraining_failed", job_id=job.job_id, error=str(exc))
        else:
            job.status = "succeeded"
            job.result = {
                "version": summary["version"],
                "metrics": summary["metrics"],
                "seed": seed,
            }
            FRAUD_RETRAINING_RUNS_TOTAL.labels(status="succeeded").inc()
            self._record_model_version(summary)
            if self._model_loader is not None:
                self._model_loader.invalidate()
            logger.info("retraining_succeeded", job_id=job.job_id, version=summary["version"])
        finally:
            job.finished_at = datetime.now(UTC)

    @staticmethod
    def _record_model_version(summary: dict[str, Any]) -> None:
        """Best-effort ``model_versions`` catalog row for the new challenger."""
        from fraud_detection.db.session import get_session_factory

        settings = get_settings()
        try:
            with get_session_factory()() as session:
                ModelVersionRepository(session).add(
                    ModelVersion(
                        name=settings.model_name,
                        version=str(summary["version"]),
                        stage="challenger",
                        auc=summary["metrics"].get("auc"),
                        registered_at=datetime.now(UTC),
                    )
                )
                session.commit()
        except Exception:  # noqa: BLE001 - policy: catalog row is best-effort
            FRAUD_DB_ERRORS_TOTAL.inc()
            logger.warning("model_version_record_failed", version=summary.get("version"))


# Backwards-compatible name used by earlier revisions of the API layer.
RetrainingTrigger = RetrainingService
